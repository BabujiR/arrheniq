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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Master data end to end (MockMvc + PostgreSQL 16): test library CRUD, product workflow with e-signature,
 * site scope, segregation of duties, reasons for change, optimistic locking and the audit trail.
 */
@Requirement({"URS-MD-001", "URS-MD-002", "URS-IAM-005", "URS-AUD-001", "URS-AUD-002", "URS-AUD-004",
        "URS-ESG-001", "URS-ESG-002", "URS-ESG-003", "URS-TEN-002"})
class MasterDataIT extends PostgresIntegrationTest {

    private static final Pattern VERSION = Pattern.compile("\"version\":(\\d+)");
    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

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
    private UUID site1;
    private UUID site2;
    private Actor manager;      // STABILITY_MANAGER at S1
    private Actor approver;     // APPROVER_QA at S1
    private Actor both;         // STABILITY_MANAGER + APPROVER_QA at S1 (for segregation of duties)
    private Actor managerS2;    // STABILITY_MANAGER at S2 only

    /** A signed-in user with its own session and CSRF cookie. */
    private record Actor(UUID id, OAuth2AuthenticationToken auth, MockHttpSession session, Cookie xsrf) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("md");
        site1 = siteId("S1");
        site2 = addSite("S2", "Second site");
        manager = actor("mgr", "Sam Manager", List.of(role("STABILITY_MANAGER", "S1")));
        approver = actor("qa", "Quinn Approver", List.of(role("APPROVER_QA", "S1")));
        both = actor("both", "Bo Both", List.of(role("STABILITY_MANAGER", "S1"), role("APPROVER_QA", "S1")));
        managerS2 = actor("mgr2", "Max Manager", List.of(role("STABILITY_MANAGER", "S2")));
    }

    // ------------------------------------------------------------------ test library

    @Test
    void testLibraryChangesAreVersionedAndAuditedFieldByField() throws Exception {
        MvcResult created = send(manager, post("/api/v1/test-methods"), """
                {"code":"assay","name":"Assay","resultsFormat":"NUMERIC","unitCode":"% LC","replicates":3,
                 "decimals":1,"defaultLowerLimit":90.0,"defaultUpperLimit":110.0}""");
        assertEquals(201, created.getResponse().getStatus(), body(created));
        String id = first(ID, body(created));
        assertEquals("0", first(VERSION, body(created)));

        MvcResult updated = send(manager, put("/api/v1/test-methods/" + id), """
                {"expectedVersion":0,"data":{"code":"ASSAY","name":"Assay (HPLC)","resultsFormat":"NUMERIC",
                 "unitCode":"% LC","replicates":3,"decimals":1,"defaultLowerLimit":90,"defaultUpperLimit":110}}""");
        assertEquals(200, updated.getResponse().getStatus(), body(updated));
        assertEquals("1", first(VERSION, body(updated)));

        MvcResult stale = send(manager, put("/api/v1/test-methods/" + id), """
                {"expectedVersion":0,"data":{"code":"ASSAY","name":"Assay v3","resultsFormat":"NUMERIC"}}""");
        assertEquals(409, stale.getResponse().getStatus());
        assertTrue(body(stale).contains("ARQ-CONC-001"), body(stale));

        List<String> trail = auditTrail("TEST_METHOD", UUID.fromString(id));
        assertEquals(List.of("CREATE", "UPDATE"), actions(trail));
        // only the changed field is recorded, with old and new values (90.0 vs 90 is not a change)
        assertTrue(trail.get(1).contains("\"field\": \"name\"") && !trail.get(1).contains("defaultLowerLimit"),
                trail.get(1));
    }

    @Test
    void invalidInputReturnsAllFieldErrors() throws Exception {
        MvcResult r = send(manager, post("/api/v1/test-methods"), """
                {"code":"bad code!","name":"","resultsFormat":"TEXT","unitCode":"mg"}""");
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(body(r).contains("\"code\":\"FORMAT\"") && body(r).contains("\"field\":\"unitCode\""), body(r));
    }

    @Test
    void writingNeedsThePermission() throws Exception {
        MvcResult r = send(approver, post("/api/v1/test-methods"), """
                {"code":"X1","name":"X","resultsFormat":"TEXT"}""");
        assertEquals(403, r.getResponse().getStatus(), body(r));
    }

    @Test
    void retiringATestNeedsAReason() throws Exception {
        String id = first(ID, body(send(manager, post("/api/v1/test-methods"), """
                {"code":"PH","name":"pH","resultsFormat":"NUMERIC"}""")));
        assertEquals(422, send(manager, post("/api/v1/test-methods/" + id + "/retire"), """
                {"expectedVersion":0}""").getResponse().getStatus());
        MvcResult ok = send(manager, post("/api/v1/test-methods/" + id + "/retire"), """
                {"expectedVersion":0,"reason":"Replaced by PH-2"}""");
        assertEquals(200, ok.getResponse().getStatus(), body(ok));
        assertTrue(body(ok).contains("\"status\":\"RETIRED\""));
    }

    // ------------------------------------------------------------------ products

    @Test
    void productApprovalIsAnESignatureBoundToTheReviewedContent() throws Exception {
        String id = createProduct(manager, site1, "MET-500");
        assertEquals(200, send(manager, post("/api/v1/products/" + id + "/submit"), """
                {"expectedVersion":0}""").getResponse().getStatus());

        assertEquals(403, send(manager, post("/api/v1/products/" + id + "/approve"), """
                {"expectedVersion":1}""").getResponse().getStatus(), "a manager can't approve");

        MvcResult noReauth = send(approver, post("/api/v1/products/" + id + "/approve"), """
                {"expectedVersion":1}""");
        assertEquals(401, noReauth.getResponse().getStatus());
        assertTrue(body(noReauth).contains("ARQ-AUTH-REAUTH"), body(noReauth));

        SecurityTestSupport.grantSigningReauth(approver.session(), tenant, approver.id());
        MvcResult approved = send(approver, post("/api/v1/products/" + id + "/approve"), """
                {"expectedVersion":1,"comment":"Reviewed against the dossier"}""");
        assertEquals(200, approved.getResponse().getStatus(), body(approved));
        assertTrue(body(approved).contains("\"status\":\"APPROVED\""), body(approved));
        assertTrue(body(approved).contains("\"signerName\":\"Quinn Approver\"")
                && body(approved).contains("\"meaning\":\"APPROVED\"")
                && body(approved).contains("\"status\":\"VALID\""), body(approved));

        assertTrue(!SecurityTestSupport.hasSigningReauth(approver.session()), "re-authentication is single-use");

        // editing the approved product needs a reason, supersedes the signature and returns it to DRAFT
        String edit = """
                {"expectedVersion":2,%s"data":{"siteId":"%s","code":"MET-500","name":"Metformin HCl ER 500 mg",
                 "productType":"DRUG_PRODUCT","expirationValue":36,"expirationUnit":"MONTH"}}""";
        assertEquals(422, send(manager, put("/api/v1/products/" + id), edit.formatted("", site1))
                .getResponse().getStatus());
        MvcResult edited = send(manager, put("/api/v1/products/" + id),
                edit.formatted("\"reason\":\"Shelf life extended per stability data\",", site1));
        assertEquals(200, edited.getResponse().getStatus(), body(edited));
        assertTrue(body(edited).contains("\"status\":\"DRAFT\"") && body(edited).contains("\"status\":\"SUPERSEDED\""),
                body(edited));

        assertEquals(List.of("CREATE", "SUBMIT", "SIGN", "APPROVE", "UPDATE", "SIGNATURE_SUPERSEDE"),
                actions(auditTrail("PRODUCT", UUID.fromString(id))));
    }

    @Test
    void theAuthorOrSubmitterCannotApprove() throws Exception {
        String id = createProduct(both, site1, "SOD-1");
        send(both, post("/api/v1/products/" + id + "/submit"), "{\"expectedVersion\":0}");
        SecurityTestSupport.grantSigningReauth(both.session(), tenant, both.id());
        MvcResult r = send(both, post("/api/v1/products/" + id + "/approve"), "{\"expectedVersion\":1}");
        assertEquals(403, r.getResponse().getStatus());
        assertTrue(body(r).contains("ARQ-SOD-001"), body(r));
    }

    @Test
    void rejectionNeedsAReasonAndReturnsToDraft() throws Exception {
        String id = createProduct(manager, site1, "REJ-1");
        send(manager, post("/api/v1/products/" + id + "/submit"), "{\"expectedVersion\":0}");
        assertEquals(422, send(approver, post("/api/v1/products/" + id + "/reject"), "{\"expectedVersion\":1}")
                .getResponse().getStatus());
        MvcResult r = send(approver, post("/api/v1/products/" + id + "/reject"),
                "{\"expectedVersion\":1,\"reason\":\"Strength missing unit\"}");
        assertEquals(200, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("\"status\":\"DRAFT\""));
    }

    @Test
    void productsOfOtherSitesAreInvisible() throws Exception {
        String other = createProduct(managerS2, site2, "S2-ONLY");
        assertEquals(404, call(manager, get("/api/v1/products/" + other)).getResponse().getStatus());
        String list = body(call(manager, get("/api/v1/products")));
        assertTrue(!list.contains("S2-ONLY"), list);
        assertEquals(403, send(manager, post("/api/v1/products"), productJson(site2, "S2-NOPE"))
                .getResponse().getStatus(), "creating at a site without product.manage there");
    }

    // ------------------------------------------------------------------ helpers

    private String createProduct(Actor who, UUID site, String code) throws Exception {
        MvcResult r = send(who, post("/api/v1/products"), productJson(site, code));
        assertEquals(201, r.getResponse().getStatus(), body(r));
        return first(ID, body(r));
    }

    private static String productJson(UUID site, String code) {
        return """
                {"siteId":"%s","code":"%s","name":"Metformin HCl ER 500 mg","productType":"DRUG_PRODUCT",
                 "dosageForm":"ER tablet","strength":"500 mg","expirationValue":24,"expirationUnit":"MONTH"}"""
                .formatted(site, code);
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
        UUID id = TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(s -> {
            audit.open("it.invite");
            UUID uid = invitations.invite(AuditActor.system("it"), new UserInvitationService.Invitation(
                    username, username + "@md.test", name, roles));
            users.linkInvitation(users.findById(uid).orElseThrow(), SecurityTestSupport.ISSUER, subject);
            return uid;
        }));
        OAuth2AuthenticationToken auth = SecurityTestSupport.authentication(tenant, "md", id, subject, username, name);
        MockHttpSession session = SecurityTestSupport.activeSession();
        Cookie xsrf = mvc.perform(get("/api/v1/me").with(authentication(auth)).session(session))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertTrue(xsrf != null, "CSRF cookie");
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

    private UUID addSite(String code, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = tenantOwnerConnection();
             PreparedStatement i = c.prepareStatement(
                     "INSERT INTO app.site (tenant_id, id, code, name) VALUES (app.current_tenant(), ?, ?, ?)")) {
            i.setObject(1, id);
            i.setString(2, code);
            i.setString(3, name);
            i.executeUpdate();
            c.commit();
        }
        return id;
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

    /** Owner connection inside this tenant with an audit context (test setup only). */
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
        return r.getResponse().getContentAsString();
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        assertTrue(m.find(), "no " + p + " in " + s);
        return m.group(1);
    }
}
