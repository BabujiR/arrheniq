package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.FieldChange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns an identity proven by the IdP into an ArrheniQ user of the tenant being logged into
 * (multi-tenancy.md §2, ADR 0005). Denials are audited with their reason; the browser only learns
 * "access denied". During a re-authentication the result must be the same user with a fresh
 * {@code auth_time} ({@link ReauthPolicy}).
 */
@Component
public class ArrheniqOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    static final String ACCESS_DENIED = "access_denied";
    static final String REAUTH_FAILED = "reauth_failed";

    private final OidcUserService delegate = new OidcUserService();
    private final TenantResolver tenants;
    private final UserDirectory users;
    private final SecurityAuditor auditor;
    private final ArrheniqSecurityProperties props;
    private final Clock clock;

    public ArrheniqOidcUserService(TenantResolver tenants, UserDirectory users, SecurityAuditor auditor,
                                   ArrheniqSecurityProperties props, Clock clock) {
        this.tenants = tenants;
        this.users = users;
        this.auditor = auditor;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser idp = delegate.loadUser(request);
        HttpServletRequest http = currentRequest();
        ResolvedTenant tenant = tenants.resolve(http).orElseThrow(() -> fail(ACCESS_DENIED));

        // Spring Security already validated iss; a token without it is refused like any other denial
        String issuer = Optional.ofNullable(idp.getIdToken().getIssuer()).map(Object::toString)
                .orElseThrow(() -> fail(ACCESS_DENIED));
        String subject = Optional.ofNullable(idp.getSubject()).orElseThrow(() -> fail(ACCESS_DENIED));
        String idpName = Optional.ofNullable(idp.getPreferredUsername()).orElse(subject);
        boolean emailVerified = Boolean.TRUE.equals(idp.getEmailVerified());

        /* The decision and the user it is about (none when the IdP identity matches no ArrheniQ user). */
        record Lookup(LoginDecision decision, LoginDecision.@Nullable UserRecord user) { }
        Lookup lookup = auditor.inTenantTransaction(tenant.id(), "security.login", () -> {
            Optional<LoginDecision.UserRecord> bySubject = users.findBySubject(issuer, subject);
            Optional<LoginDecision.UserRecord> invitation = bySubject.isPresent()
                    ? Optional.empty() : users.findInvitationByEmail(idp.getEmail());
            LoginDecision d = LoginDecision.decide(tenant.status(), bySubject, invitation, emailVerified);
            if (d.outcome() == LoginDecision.Outcome.LINK_INVITATION) {   // only decided when there is an invitation
                users.linkInvitation(invitation.orElseThrow(), issuer, subject);
            }
            return new Lookup(d, bySubject.or(() -> invitation).orElse(null));
        });

        LoginDecision.UserRecord user = lookup.user();
        if (!lookup.decision().allowed() || user == null) {
            AuditActor actor = user != null
                    ? new AuditActor(user.id(), user.displayName(), AuditActor.Type.USER)
                    : new AuditActor(null, "idp:" + idpName, AuditActor.Type.SYSTEM);
            LoginDecision.DenyReason reason = lookup.decision().denyReason();
            auditor.record(tenant.id(), actor, "LOGIN_FAILURE", lookup.decision().userId(), idpName,
                    List.of(new FieldChange("reason", null, reason != null ? reason.name() : "UNKNOWN_USER"),
                            new FieldChange("idpIssuer", null, issuer)));
            throw fail(ACCESS_DENIED);
        }
        verifyReauthIfPending(http, tenant, user, idp);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ARQ_USER"));
        return new ArrheniqUser(authorities, idp.getIdToken(), idp.getUserInfo(), tenant.id(), tenant.slug(),
                user.id(), user.username(), user.displayName());
    }

    private void verifyReauthIfPending(HttpServletRequest http, ResolvedTenant tenant, LoginDecision.UserRecord user,
                                       OidcUser idp) {
        HttpSession session = http.getSession(false);
        if (session == null || !(session.getAttribute(SessionKeys.PENDING_REAUTH) instanceof ReauthRequest pending)) {
            return;
        }
        ReauthPolicy.Verdict verdict = ReauthPolicy.verifyCallback(pending, tenant.id(), user.id(),
                idp.getAuthenticatedAt(), clock.instant(), props.reauthFlowTimeout());
        if (verdict != ReauthPolicy.Verdict.OK) {
            auditor.record(pending.tenantId(), AuditActor.system("reauth"), "REAUTH_FAILURE", pending.userId(),
                    user.username(), List.of(new FieldChange("verdict", null, verdict.name()),
                            new FieldChange("purpose", null, pending.purpose().name()),
                            new FieldChange("authenticatedUser", null, user.id())));
            throw fail(REAUTH_FAILED);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        throw new IllegalStateException("ARQ-AUTH-002: OIDC login outside an HTTP request");
    }

    private static OAuth2AuthenticationException fail(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, "Access denied", null));
    }
}
