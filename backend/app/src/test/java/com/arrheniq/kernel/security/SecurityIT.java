package com.arrheniq.kernel.security;

import com.arrheniq.identity.UserInvitationService;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Browser security end to end with MockMvc and real PostgreSQL: deny by default, login redirects,
 * per-request permission loading, idle lock, absolute expiry, revocation, re-authentication request,
 * CSRF on logout, and the audit events these produce. The real IdP round trip is {@link KeycloakLoginIT}.
 */
@Requirement({"URS-SEC-001", "URS-IAM-004", "URS-IAM-005", "URS-ESG-002", "URS-AUD-001"})
class SecurityIT extends PostgresIntegrationTest {

    private static final String ISSUER = "http://idp.invalid";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserInvitationService invitations;
    @Autowired
    private UserDirectory users;
    @Autowired
    private AuditService audit;
    @Autowired
    private PlatformTransactionManager txManager;

    private MockMvc mvc;
    private UUID tenant;
    private UUID userId;
    private String subject;

    @BeforeEach
    void setUp() throws SQLException {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("sec");
        subject = "sub-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        userId = TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(_ -> {
            audit.open("it.invite");
            UUID id = invitations.invite(AuditActor.system("it"), new UserInvitationService.Invitation(
                    "smanager", "smanager@sec.test", "Sam Manager",
                    List.of(new UserInvitationService.RoleAssignment("STABILITY_MANAGER", "S1"))));
            users.linkInvitation(users.findById(id).orElseThrow(), ISSUER, subject);
            return id;
        }));
    }

    @Test
    void apiWithoutSessionGets401ProblemJson() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/me")).andReturn();
        assertEquals(401, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("ARQ-AUTH-401"));
    }

    @Test
    void unknownPathsAreDenied() throws Exception {
        int status = mvc.perform(get("/internal/anything").with(authentication(auth()))
                .session(activeSession())).andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void loginRedirectsToTheIdpWithPkceAndNonce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult start = mvc.perform(get("/auth/login").param("returnTo", "/studies/42").session(session)).andReturn();
        assertEquals("/oauth2/authorization/arrheniq", start.getResponse().getRedirectedUrl());
        assertEquals("/studies/42", session.getAttribute(SessionKeys.RETURN_TO));

        String idp = mvc.perform(get("/oauth2/authorization/arrheniq").session(session))
                .andReturn().getResponse().getRedirectedUrl();
        assertTrue(idp.startsWith(ISSUER + "/auth?"), idp);
        assertTrue(idp.contains("code_challenge_method=S256"), idp);
        assertTrue(idp.contains("nonce="), idp);
        assertFalse(idp.contains("prompt=login"), "a normal login must not force re-authentication");
    }

    @Test
    void openRedirectTargetsAreIgnored() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/auth/login").param("returnTo", "https://evil.example/").session(session));
        assertNull(session.getAttribute(SessionKeys.RETURN_TO));
    }

    @Test
    void meReturnsPermissionsAndSiteScopeFromTheDatabase() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/me").with(authentication(auth())).session(activeSession())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("\"username\":\"smanager\""), body);
        assertTrue(body.contains("study.create"), body);
        assertFalse(body.contains("result.approve.final"), "stability manager must not have QA approval: " + body);
        assertTrue(body.contains("\"allSites\":false"), body);
        assertTrue(body.contains("\"sites\":[{") && body.contains("\"code\":\"S1\""), "site picker data: " + body);
        assertNotNull(r.getResponse().getCookie("XSRF-TOKEN"), "CSRF cookie must be issued: "
                + r.getResponse().getHeaders("Set-Cookie"));
    }

    @Test
    void idleSessionIsLockedButReauthenticationIsStillReachable() throws Exception {
        MockHttpSession session = activeSession();
        session.setAttribute(SessionKeys.LAST_ACTIVITY, Instant.now().minus(Duration.ofMinutes(31)));
        MvcResult locked = mvc.perform(get("/api/v1/me").with(authentication(auth())).session(session)).andReturn();
        assertEquals(401, locked.getResponse().getStatus());
        assertTrue(locked.getResponse().getContentAsString().contains("ARQ-AUTH-LOCKED"));
        assertEquals(Boolean.TRUE, session.getAttribute(SessionKeys.LOCKED));

        MvcResult unlock = mvc.perform(get("/auth/reauth").param("purpose", "UNLOCK").param("returnTo", "/")
                .with(authentication(auth())).session(session)).andReturn();
        assertEquals(302, unlock.getResponse().getStatus());
        assertTrue(auditActions().contains("SESSION_LOCKED"));
    }

    @Test
    void sessionExpiresAfterItsAbsoluteLifetime() throws Exception {
        MockHttpSession session = activeSession();
        session.setAttribute(SessionKeys.LOGIN_AT, Instant.now().minus(Duration.ofHours(13)));
        MvcResult r = mvc.perform(get("/api/v1/me").with(authentication(auth())).session(session)).andReturn();
        assertEquals(401, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("ARQ-AUTH-EXPIRED"));
        assertTrue(session.isInvalid());
        assertTrue(auditActions().contains("SESSION_EXPIRED"));
    }

    @Test
    void deactivatedUserLosesAccessOnTheNextRequest() throws Exception {
        MockHttpSession session = activeSession();
        assertEquals(200, mvc.perform(get("/api/v1/me").with(authentication(auth())).session(session))
                .andReturn().getResponse().getStatus());
        try (Connection c = ownerConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement t = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true),"
                    + " set_config('app.audit_ctx', 'it|deactivate', true)")) {
                t.setString(1, tenant.toString());
                t.execute();
            }
            try (PreparedStatement u = c.prepareStatement("UPDATE app.app_user SET status = 'DEACTIVATED',"
                    + " deactivated_at = now() WHERE id = ?")) {
                u.setObject(1, userId);
                assertEquals(1, u.executeUpdate());
            }
            c.commit();
        }
        MvcResult r = mvc.perform(get("/api/v1/me").with(authentication(auth())).session(session)).andReturn();
        assertEquals(401, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("ARQ-AUTH-REVOKED"));
        assertTrue(auditActions().contains("SESSION_REVOKED"));
    }

    @Test
    void reauthenticationForcesPromptLoginAndMaxAgeZero() throws Exception {
        MockHttpSession session = activeSession();
        assertEquals(400, mvc.perform(get("/auth/reauth").param("purpose", "SIGN").param("returnTo", "/results/7")
                .with(authentication(auth())).session(session)).andReturn().getResponse().getStatus(),
                "a signature re-authentication must name the record it is for");

        UUID record = UUID.randomUUID();
        MvcResult start = mvc.perform(get("/auth/reauth").param("purpose", "SIGN").param("returnTo", "/results/7")
                .param("recordType", "PRODUCT").param("recordId", record.toString()).param("recordVersion", "4")
                .with(authentication(auth())).session(session)).andReturn();
        assertEquals("/oauth2/authorization/arrheniq?reauth=1", start.getResponse().getRedirectedUrl());
        ReauthRequest pending = (ReauthRequest) session.getAttribute(SessionKeys.PENDING_REAUTH);
        assertEquals(userId, pending.userId());
        assertEquals(ReauthRequest.Purpose.SIGN, pending.purpose());
        SigningIntent intent = pending.intent();
        assertNotNull(intent, "SIGN must carry the signing intent");
        assertTrue(intent.covers("PRODUCT", record, 4), intent.toString());

        String idp = mvc.perform(get("/oauth2/authorization/arrheniq").param("reauth", "1")
                .with(authentication(auth())).session(session)).andReturn().getResponse().getRedirectedUrl();
        assertTrue(idp.contains("prompt=login"), idp);
        assertTrue(idp.contains("max_age=0"), idp);
        assertTrue(idp.contains("login_hint=smanager"), idp);
    }

    /**
     * The real SPA flow (ADR 0006): read the XSRF-TOKEN cookie, send it back in the X-XSRF-TOKEN header.
     * Deliberately not using Spring's csrf() test helper: it replaces the CsrfFilter's token repository
     * in the shared application context, which would hide the cookie from every later test.
     */
    @Test
    void logoutNeedsTheSpaCsrfTokenAndIsAudited() throws Exception {
        MockHttpSession session = activeSession();
        MvcResult me = mvc.perform(get("/api/v1/me").with(authentication(auth())).session(session)).andReturn();
        Cookie xsrf = me.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "CSRF cookie must be issued: " + me.getResponse().getHeaders("Set-Cookie"));

        assertEquals(403, mvc.perform(post("/auth/logout").with(authentication(auth())).session(session))
                .andReturn().getResponse().getStatus(), "logout without CSRF token must be refused");
        assertEquals(403, mvc.perform(post("/auth/logout").cookie(xsrf).header("X-XSRF-TOKEN", "forged")
                .with(authentication(auth())).session(session)).andReturn().getResponse().getStatus(),
                "a wrong token must be refused");

        MvcResult ok = mvc.perform(post("/auth/logout").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                .with(authentication(auth())).session(session)).andReturn();
        assertEquals(302, ok.getResponse().getStatus());
        assertTrue(auditActions().contains("LOGOUT"));
    }

    // ------------------------------------------------------------------ helpers

    private OAuth2AuthenticationToken auth() {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("it-token", now, now.plusSeconds(300), Map.of(
                "iss", ISSUER, "sub", subject, "aud", List.of("it-client"),
                "preferred_username", "smanager", "auth_time", now.getEpochSecond()));
        ArrheniqUser user = new ArrheniqUser(List.of(), idToken, null, tenant, "sec", userId, "smanager", "Sam Manager");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "arrheniq");
    }

    private static MockHttpSession activeSession() {
        MockHttpSession session = new MockHttpSession();
        Instant now = Instant.now();
        session.setAttribute(SessionKeys.LOGIN_AT, now.minusSeconds(60));
        session.setAttribute(SessionKeys.LAST_ACTIVITY, now.minusSeconds(60));
        session.setAttribute(SessionKeys.IDLE_LOCK, Duration.ofMinutes(30));
        session.setAttribute(SessionKeys.LOGIN_HINT, "smanager");
        return session;
    }

    private List<String> auditActions() throws SQLException {
        try (Connection c = ownerConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement t = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                t.setString(1, tenant.toString());
                t.execute();
            }
            try (PreparedStatement q = c.prepareStatement("SELECT action FROM audit.audit_event ORDER BY seq");
                 ResultSet rs = q.executeQuery()) {
                List<String> actions = new java.util.ArrayList<>();
                while (rs.next()) {
                    actions.add(rs.getString(1));
                }
                c.commit();
                return actions;
            }
        }
    }
}
