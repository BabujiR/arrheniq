package com.arrheniq.masterdata;

import com.arrheniq.identity.UserInvitationService;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.security.SecurityTestSupport;
import com.arrheniq.kernel.security.UserDirectory;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Specifications over the real HTTP API, security and database (URS-MD-003, MD-004, ESG-001..003): limits from
 * the test library, drafts, approval by e-signature, effective and superseded versions, segregation of duties,
 * site scope, audit trail.
 */
@Requirement({"URS-MD-003", "URS-MD-004", "URS-ESG-001", "URS-ESG-002", "URS-AUD-001", "URS-AUD-002"})
class SpecificationIT extends PostgresIntegrationTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern VERSION = Pattern.compile("\"version\":(\\d+)");
    private static final Pattern FIRST_VERSION_ID = Pattern.compile("\"versions\":\\[\\{\"id\":\"([0-9a-f-]{36})\"");

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
    private Actor manager;      // STABILITY_MANAGER at S1 (spec.manage, test.manage, product.manage)
    private Actor approver;     // APPROVER_QA at S1 (spec.approve)
    private Actor both;         // both roles at S1
    private Actor managerS2;    // STABILITY_MANAGER at S2 only
    private UUID product;
    private String assay;
    private String impurity;
    private String appearance;

    private record Actor(UUID id, OAuth2AuthenticationToken auth, MockHttpSession session, Cookie xsrf) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("spec");
        UUID site1 = siteId("S1");
        addSite("S2", "Second site");
        manager = actor("mgr", "Sam Manager", List.of(role("STABILITY_MANAGER", "S1")));
        approver = actor("qa", "Quinn Approver", List.of(role("APPROVER_QA", "S1")));
        both = actor("both", "Bo Both", List.of(role("STABILITY_MANAGER", "S1"), role("APPROVER_QA", "S1")));
        managerS2 = actor("mgr2", "Max Manager", List.of(role("STABILITY_MANAGER", "S2")));
        assay = createTest("""
                {"code":"ASSAY","name":"Assay (HPLC)","resultsFormat":"NUMERIC","unitCode":"%LC","replicates":2,
                 "decimals":1,"defaultLowerLimit":95,"defaultUpperLimit":105}""");
        impurity = createTest("""
                {"code":"IMP-B","name":"Impurity B","resultsFormat":"NUMERIC","unitCode":"%","replicates":1,
                 "decimals":2}""");
        appearance = createTest("{\"code\":\"APP\",\"name\":\"Appearance\",\"resultsFormat\":\"TEXT\"}");
        MvcResult p = send(manager, post("/api/v1/products"), """
                {"siteId":"%s","code":"MET-500","name":"Metformin HCl ER 500 mg","productType":"DRUG_PRODUCT",
                 "expirationValue":24,"expirationUnit":"MONTH"}""".formatted(site1));
        assertEquals(201, p.getResponse().getStatus(), body(p));
        product = UUID.fromString(first(ID, body(p)));
    }

    @Test
    void aSpecificationIsDraftedApprovedAndReplacedByANewVersion() throws Exception {
        MvcResult created = send(manager, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"spec-met-500","title":"Metformin ER 500 mg - stability"}"""
                .formatted(product));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        assertTrue(body(created).contains("\"specNumber\":\"SPEC-MET-500\""), body(created));
        String specId = first(ID, body(created));
        String v1 = first(FIRST_VERSION_ID, body(created));

        // draft: two-sided assay with alert limits, one-sided impurity (NMT 0.5 %), text limit for appearance
        MvcResult saved = send(manager, put("/api/v1/spec-versions/" + v1), """
                {"expectedVersion":0,"data":{"changeSummary":"Initial","tests":[
                  {"testMethodId":"%s","lowerLimit":90.0,"upperLimit":110.0,"alertLower":92,"alertUpper":108},
                  {"testMethodId":"%s","upperLimit":0.5},
                  {"testMethodId":"%s","textLimit":"White, round, film-coated tablet"}]}}"""
                .formatted(assay, impurity, appearance));
        assertEquals(200, saved.getResponse().getStatus(), body(saved));
        assertTrue(body(saved).contains("\"lowerLimit\":90.0") && body(saved).contains("\"unitCode\":\"%LC\"")
                && body(saved).contains("\"textLimit\":\"White, round, film-coated tablet\""), body(saved));
        assertEquals("1", first(VERSION, body(saved)));

        assertEquals(200, send(manager, post("/api/v1/spec-versions/" + v1 + "/submit"), "{\"expectedVersion\":1}")
                .getResponse().getStatus());
        assertEquals(409, send(manager, put("/api/v1/spec-versions/" + v1), """
                {"expectedVersion":2,"data":{"tests":[]}}""").getResponse().getStatus(), "no edits in review");

        MvcResult approved = approve(approver, v1, 2);
        assertEquals(200, approved.getResponse().getStatus(), body(approved));
        assertTrue(body(approved).contains("\"status\":\"EFFECTIVE\"")
                && body(approved).contains("\"effectiveFrom\":\"" + LocalDate.now() + "\"")
                && body(approved).contains("\"meaning\":\"APPROVED\""), body(approved));
        assertEquals(409, send(manager, put("/api/v1/spec-versions/" + v1), """
                {"expectedVersion":3,"data":{"tests":[]}}""").getResponse().getStatus(),
                "an effective version never changes");

        // the change is a new version: a copy of the effective one
        MvcResult next = send(manager, post("/api/v1/specifications/" + specId + "/versions"), "");
        assertEquals(201, next.getResponse().getStatus(), body(next));
        String v2 = first(ID, body(next));
        assertTrue(body(next).contains("\"versionNo\":2") && body(next).contains("\"upperLimit\":0.5"), body(next));
        MvcResult another = send(manager, post("/api/v1/specifications/" + specId + "/versions"), "");
        assertEquals(409, another.getResponse().getStatus(), "one open version at a time");
        assertTrue(body(another).contains("ARQ-MD-020"), body(another));

        assertEquals(200, send(manager, put("/api/v1/spec-versions/" + v2), """
                {"expectedVersion":0,"data":{"changeSummary":"Impurity B tightened per CC-2026-014","tests":[
                  {"testMethodId":"%s","lowerLimit":90.0,"upperLimit":110.0,"alertLower":92,"alertUpper":108},
                  {"testMethodId":"%s","upperLimit":0.3},
                  {"testMethodId":"%s","textLimit":"White, round, film-coated tablet"}]}}"""
                .formatted(assay, impurity, appearance)).getResponse().getStatus());
        List<String> v2Trail = auditTrail("SPEC_VERSION", UUID.fromString(v2));
        assertTrue(v2Trail.get(1).startsWith("UPDATE") && v2Trail.get(1).contains("test.IMP-B.upperLimit")
                && !v2Trail.get(1).contains("test.ASSAY"), "only the changed limit is audited: " + v2Trail.get(1));

        send(manager, post("/api/v1/spec-versions/" + v2 + "/submit"), "{\"expectedVersion\":1}");
        assertEquals(200, approve(approver, v2, 2).getResponse().getStatus());

        String spec = body(call(manager, get("/api/v1/specifications/" + specId)));
        assertTrue(spec.contains("\"effectiveVersionNo\":2") && spec.contains("\"status\":\"SUPERSEDED\""), spec);
        assertEquals(List.of("CREATE", "UPDATE", "SUBMIT", "SIGN", "APPROVE", "SUPERSEDE"),
                actions(auditTrail("SPEC_VERSION", UUID.fromString(v1))));
    }

    @Test
    void limitsAreCheckedAndAnEmptyDraftCannotBeSubmitted() throws Exception {
        String v1 = newSpec("SPEC-CHK");
        assertEquals(400, send(manager, post("/api/v1/spec-versions/" + v1 + "/submit"), "{\"expectedVersion\":0}")
                .getResponse().getStatus(), "a specification needs at least one test");
        MvcResult bad = send(manager, put("/api/v1/spec-versions/" + v1), """
                {"expectedVersion":0,"data":{"tests":[
                  {"testMethodId":"%s","lowerLimit":110,"upperLimit":90},
                  {"testMethodId":"%s","upperLimit":0.5,"alertUpper":0.7},
                  {"testMethodId":"%s","textLimit":"Conforms","lowerLimit":1}]}}"""
                .formatted(assay, impurity, appearance));
        assertEquals(400, bad.getResponse().getStatus(), body(bad));
        assertTrue(body(bad).contains("tests[0].lowerLimit") && body(bad).contains("tests[1].alertUpper")
                && body(bad).contains("tests[2].lowerLimit"), body(bad));
    }

    @Test
    void theAuthorOrSubmitterCannotApprove() throws Exception {
        MvcResult created = send(both, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"SPEC-SOD","title":"SoD"}""".formatted(product));
        String v1 = first(FIRST_VERSION_ID, body(created));
        send(both, put("/api/v1/spec-versions/" + v1), """
                {"expectedVersion":0,"data":{"tests":[{"testMethodId":"%s","upperLimit":0.5}]}}""".formatted(impurity));
        send(both, post("/api/v1/spec-versions/" + v1 + "/submit"), "{\"expectedVersion\":1}");
        MvcResult r = approve(both, v1, 2);
        assertEquals(403, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("ARQ-SOD-001"), body(r));
    }

    @Test
    void specificationsFollowTheProductsSite() throws Exception {
        String v1 = newSpec("SPEC-S1");
        assertEquals(404, call(managerS2, get("/api/v1/spec-versions/" + v1)).getResponse().getStatus());
        assertFalse(body(call(managerS2, get("/api/v1/specifications"))).contains("SPEC-S1"));
        assertEquals(404, send(managerS2, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"SPEC-X","title":"x"}""".formatted(product)).getResponse().getStatus(),
                "a product of another site is not visible");
    }

    @Test
    void discardingVersionOneDiscardsTheSpecification() throws Exception {
        MvcResult created = send(manager, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"SPEC-TMP","title":"Mistake"}""".formatted(product));
        String specId = first(ID, body(created));
        String v1 = first(FIRST_VERSION_ID, body(created));
        assertEquals(422, send(manager, post("/api/v1/spec-versions/" + v1 + "/discard"), "{\"expectedVersion\":0}")
                .getResponse().getStatus(), "discarding needs a reason");
        assertEquals(204, send(manager, post("/api/v1/spec-versions/" + v1 + "/discard"),
                "{\"expectedVersion\":0,\"reason\":\"Created for the wrong product\"}").getResponse().getStatus());
        assertEquals(404, call(manager, get("/api/v1/specifications/" + specId)).getResponse().getStatus());
        assertEquals(List.of("CREATE", "LOGICAL_DELETE"), actions(auditTrail("SPECIFICATION", UUID.fromString(specId))));
    }

    // ------------------------------------------------------------------ helpers

    private String newSpec(String number) throws Exception {
        MvcResult created = send(manager, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"%s","title":"t"}""".formatted(product, number));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        return first(FIRST_VERSION_ID, body(created));
    }

    private MvcResult approve(Actor who, String versionId, long version) throws Exception {
        SecurityTestSupport.grantSigningReauth(who.session(), tenant, who.id(), "SPEC_VERSION",
                UUID.fromString(versionId), version);
        return send(who, post("/api/v1/spec-versions/" + versionId + "/approve"), "{\"expectedVersion\":" + version + "}");
    }

    private String createTest(String json) throws Exception {
        MvcResult r = send(manager, post("/api/v1/test-methods"), json);
        assertEquals(201, r.getResponse().getStatus(), body(r));
        return first(ID, body(r));
    }

    private MvcResult send(Actor who, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json)
                .cookie(who.xsrf()).header("X-XSRF-TOKEN", who.xsrf().getValue())
                .with(authentication(who.auth())).session(who.session())).andReturn();
    }

    private MvcResult call(Actor who, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(authentication(who.auth())).session(who.session())).andReturn();
    }

    private Actor actor(String username, String name, List<UserInvitationService.RoleAssignment> roles)
            throws Exception {
        String subject = "sub-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID id = TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(_ -> {
            audit.open("it.invite");
            UUID uid = invitations.invite(AuditActor.system("it"), new UserInvitationService.Invitation(
                    username, username + "@spec.test", name, roles));
            users.linkInvitation(users.findById(uid).orElseThrow(), SecurityTestSupport.ISSUER, subject);
            return uid;
        }));
        OAuth2AuthenticationToken auth = SecurityTestSupport.authentication(tenant, "spec", id, subject, username,
                name);
        MockHttpSession session = SecurityTestSupport.activeSession();
        Cookie xsrf = mvc.perform(get("/api/v1/me").with(authentication(auth)).session(session))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "CSRF cookie");
        return new Actor(id, auth, session, xsrf);
    }

    private static UserInvitationService.RoleAssignment role(String code, String site) {
        return new UserInvitationService.RoleAssignment(code, site);
    }

    private UUID siteId(String code) throws SQLException {
        try (Connection c = tenantOwnerConnection();
             PreparedStatement q = c.prepareStatement("SELECT id FROM app.site WHERE code = ?")) {
            q.setString(1, code);
            try (ResultSet rs = q.executeQuery()) {
                rs.next();
                UUID id = rs.getObject(1, UUID.class);
                c.commit();
                return id;
            }
        }
    }

    private void addSite(String code, String name) throws SQLException {
        try (Connection c = tenantOwnerConnection();
             PreparedStatement i = c.prepareStatement(
                     "INSERT INTO app.site (tenant_id, id, code, name) VALUES (app.current_tenant(), ?, ?, ?)")) {
            i.setObject(1, UUID.randomUUID());
            i.setString(2, code);
            i.setString(3, name);
            i.executeUpdate();
            c.commit();
        }
    }

    /** Audit events (action + changes JSON) of one record, in order. */
    private List<String> auditTrail(String recordType, UUID recordId) throws SQLException {
        try (Connection c = tenantOwnerConnection();
             PreparedStatement q = c.prepareStatement("SELECT action || ' ' || changes::text FROM audit.audit_event"
                     + " WHERE record_type = ? AND record_id = ? ORDER BY seq")) {
            q.setString(1, recordType);
            q.setObject(2, recordId);
            List<String> out = new ArrayList<>();
            try (ResultSet rs = q.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            c.commit();
            return out;
        }
    }

    private static List<String> actions(List<String> trail) {
        return trail.stream().map(e -> e.substring(0, e.indexOf(' '))).toList();
    }

    private Connection tenantOwnerConnection() throws SQLException {
        Connection c = ownerConnection();
        c.setAutoCommit(false);
        try (PreparedStatement t = c.prepareStatement(
                "SELECT set_config('app.tenant_id', ?, true), set_config('app.audit_ctx', 'it|setup', true)")) {
            t.setString(1, tenant.toString());
            t.execute();
        }
        return c;
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        assertTrue(m.find(), "no " + p + " in " + s);
        return m.group(1);
    }
}
