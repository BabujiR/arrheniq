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
import org.jspecify.annotations.Nullable;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Protocols over the real HTTP API, security and database (URS-PRO-001/002/003/005/007, ESG-001/002): start from
 * an ICH template and a specification, edit the time points and the test grid (saved repeatedly, which relies on
 * the live-row uniqueness of V14), approve by e-signature, supersede with a new version; validation, SoD, scope.
 */
@Requirement({"URS-PRO-001", "URS-PRO-002", "URS-PRO-003", "URS-PRO-005", "URS-PRO-007", "URS-ESG-001",
        "URS-ESG-002", "URS-AUD-002", "URS-AUD-006"})
class ProtocolIT extends PostgresIntegrationTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
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
    private Actor manager;      // STABILITY_MANAGER at S1 (protocol.manage)
    private Actor approver;     // APPROVER_QA at S1 (protocol.approve)
    private Actor both;
    private Actor managerS2;
    private UUID product;
    private UUID otherProduct;
    private String assay;
    private String water;
    private String specVersion;

    private record Actor(UUID id, OAuth2AuthenticationToken auth, MockHttpSession session, Cookie xsrf) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("prt");
        UUID site1 = siteId("S1");
        addSite("S2", "Second site");
        manager = actor("mgr", "Sam Manager", List.of(role("STABILITY_MANAGER", "S1")));
        approver = actor("qa", "Quinn Approver", List.of(role("APPROVER_QA", "S1")));
        both = actor("both", "Bo Both", List.of(role("STABILITY_MANAGER", "S1"), role("APPROVER_QA", "S1")));
        managerS2 = actor("mgr2", "Max Manager", List.of(role("STABILITY_MANAGER", "S2")));
        assay = createTest("""
                {"code":"ASSAY","name":"Assay (HPLC)","resultsFormat":"NUMERIC","unitCode":"%LC","replicates":2}""");
        water = createTest("""
                {"code":"KF","name":"Water (KF)","resultsFormat":"NUMERIC","unitCode":"%","replicates":1}""");
        product = createProduct(site1, "MET-500");
        otherProduct = createProduct(site1, "MET-850");
        MvcResult spec = send(manager, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"SPEC-MET-500","title":"Stability"}""".formatted(product));
        specVersion = first(FIRST_VERSION_ID, body(spec));
        assertEquals(200, send(manager, put("/api/v1/spec-versions/" + specVersion), """
                {"expectedVersion":0,"data":{"tests":[{"testMethodId":"%s","lowerLimit":90,"upperLimit":110},
                 {"testMethodId":"%s","upperLimit":0.5}]}}""".formatted(assay, water)).getResponse().getStatus());
    }

    @Test
    void aProtocolStartsFromAnIchTemplateIsPlannedApprovedAndReplaced() throws Exception {
        MvcResult created = send(manager, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"prt-met-500-lt","name":"Long-term 25/60",
                 "templateCode":"LT_ZONE_II_36M","specVersionId":"%s"}""".formatted(product, specVersion));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        String protocolId = first(ID, body(created));
        String v1 = first(FIRST_VERSION_ID, body(created));

        String draft = body(call(manager, get("/api/v1/protocol-versions/" + v1)));
        assertTrue(draft.contains("\"studyType\":\"LONG_TERM\"") && draft.contains("\"conditionCode\":\"25C60RH\"")
                && draft.contains("\"label\":\"Initial\"") && draft.contains("\"label\":\"36M\"")
                && draft.contains("\"at\":[0,1,2,3,4,5,6,7]"), "template time points, spec tests at all: " + draft);
        assertTrue(draft.contains("No units planned at Initial"), "no units planned yet: " + draft);

        // plan: water only at initial, 12M and 36M; units per time point; a 48M point added out of order
        String content = """
                {"studyType":"LONG_TERM","conditionCode":"25C60RH","reserveUnits":10,"fillUnitCode":"BOTTLE",
                 "timepoints":[{"value":48,"unit":"MONTH","unitsPlanned":3},
                   {"value":0,"unit":"MONTH","unitsPlanned":3},{"value":3,"unit":"MONTH","unitsPlanned":2},
                   {"value":6,"unit":"MONTH","unitsPlanned":2},{"value":9,"unit":"MONTH","unitsPlanned":2},
                   {"value":12,"unit":"MONTH","unitsPlanned":3},{"value":18,"unit":"MONTH","unitsPlanned":2},
                   {"value":24,"unit":"MONTH","unitsPlanned":2},{"value":36,"unit":"MONTH","unitsPlanned":3}],
                 "tests":[{"testMethodId":"%s","at":[0,1,2,3,4,5,6,7,8]},{"testMethodId":"%s","at":[1,5,%d]}]}""";
        MvcResult saved = send(manager, put("/api/v1/protocol-versions/" + v1),
                "{\"expectedVersion\":0,\"data\":" + content.formatted(assay, water, 8) + "}");
        assertEquals(200, saved.getResponse().getStatus(), body(saved));
        assertTrue(body(saved).contains("\"totalUnits\":32") && body(saved).contains("\"warnings\":[]")
                && body(saved).contains("\"label\":\"48M\""), body(saved));
        // saving again rewrites the same time points and grid (live-row uniqueness, V14)
        MvcResult again = send(manager, put("/api/v1/protocol-versions/" + v1),
                "{\"expectedVersion\":1,\"data\":" + content.formatted(assay, water, 0) + "}");
        assertEquals(200, again.getResponse().getStatus(), body(again));
        List<String> trail = auditTrail("PROTOCOL_VERSION", UUID.fromString(v1));
        assertTrue(trail.get(2).startsWith("UPDATE") && trail.get(2).contains("grid.KF")
                && !trail.get(2).contains("grid.ASSAY"), "only the water grid changed: " + trail.get(2));

        assertEquals(200, send(manager, post("/api/v1/protocol-versions/" + v1 + "/submit"),
                "{\"expectedVersion\":2}").getResponse().getStatus());
        MvcResult approved = approve(approver, v1, 3);
        assertEquals(200, approved.getResponse().getStatus(), body(approved));
        assertTrue(body(approved).contains("\"status\":\"APPROVED\"") && body(approved).contains("\"meaning\":\"APPROVED\""),
                body(approved));

        MvcResult next = send(manager, post("/api/v1/protocols/" + protocolId + "/versions"), "");
        assertEquals(201, next.getResponse().getStatus(), body(next));
        String v2 = first(ID, body(next));
        assertTrue(body(next).contains("\"totalUnits\":32"), "copied: " + body(next));
        assertEquals(200, send(manager, put("/api/v1/protocol-versions/" + v2),
                "{\"expectedVersion\":0,\"data\":" + content.formatted(assay, water, 8)
                        .replace("\"reserveUnits\":10", "\"reserveUnits\":20") + "}").getResponse().getStatus());
        send(manager, post("/api/v1/protocol-versions/" + v2 + "/submit"), "{\"expectedVersion\":1}");
        assertEquals(200, approve(approver, v2, 2).getResponse().getStatus());

        String protocol = body(call(manager, get("/api/v1/protocols/" + protocolId)));
        assertTrue(protocol.contains("\"approvedVersionNo\":2") && protocol.contains("\"status\":\"SUPERSEDED\""),
                protocol);

        // record history (URS-AUD-006): the version's trail as the page shows it; nothing for another site
        String history = body(call(manager, get("/api/v1/audit/history?recordId=" + protocolId + "&recordId=" + v1)));
        assertTrue(history.contains("\"action\":\"SIGN\"") && history.contains("\"field\":\"grid.KF\"")
                && history.contains("\"recordType\":\"PROTOCOL\"") && !history.contains("\"hash\""), history);
        assertEquals("[]", body(call(managerS2, get("/api/v1/audit/history?recordId=" + v1))));
    }

    @Test
    void mistakesAreRejectedAndAnEmptyProtocolCannotBeSubmitted() throws Exception {
        String v1 = newProtocol("PRT-CHK", null);
        assertEquals(400, send(manager, post("/api/v1/protocol-versions/" + v1 + "/submit"),
                "{\"expectedVersion\":0}").getResponse().getStatus());
        MvcResult bad = send(manager, put("/api/v1/protocol-versions/" + v1), """
                {"expectedVersion":0,"data":{"studyType":"LONG_TERM","conditionCode":"99C",
                 "timepoints":[{"value":3,"unit":"MONTH"},{"value":3,"unit":"MONTH"}],
                 "tests":[{"testMethodId":"%s","at":[]}]}}""".formatted(assay));
        assertEquals(400, bad.getResponse().getStatus(), body(bad));
        assertTrue(body(bad).contains("conditionCode") && body(bad).contains("timepoints[1].value")
                && body(bad).contains("tests[0].at"), body(bad));
        MvcResult otherSpec = send(manager, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"PRT-X","name":"x","specVersionId":"%s"}"""
                .formatted(otherProduct, specVersion));
        assertEquals(400, otherSpec.getResponse().getStatus(), body(otherSpec));
        assertTrue(body(otherSpec).contains("OTHER_PRODUCT"), body(otherSpec));
    }

    @Test
    void theAuthorCannotApproveAndOtherSitesCannotSeeIt() throws Exception {
        MvcResult created = send(both, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"PRT-SOD","name":"SoD","templateCode":"ACC_40_75_6M",
                 "specVersionId":"%s"}""".formatted(product, specVersion));
        String v1 = first(FIRST_VERSION_ID, body(created));
        assertEquals(200, send(both, put("/api/v1/protocol-versions/" + v1), """
                {"expectedVersion":0,"data":{"studyType":"ACCELERATED","timepoints":[{"value":0,"unit":"MONTH",
                 "unitsPlanned":2},{"value":6,"unit":"MONTH","unitsPlanned":2}],
                 "tests":[{"testMethodId":"%s","at":[0,1]}]}}""".formatted(assay)).getResponse().getStatus());
        send(both, post("/api/v1/protocol-versions/" + v1 + "/submit"), "{\"expectedVersion\":1}");
        MvcResult r = approve(both, v1, 2);
        assertEquals(403, r.getResponse().getStatus(), body(r));
        assertTrue(body(r).contains("ARQ-SOD-001"), body(r));
        assertEquals(404, call(managerS2, get("/api/v1/protocol-versions/" + v1)).getResponse().getStatus());
    }

    // ------------------------------------------------------------------ helpers

    private String newProtocol(String number, @Nullable String template) throws Exception {
        MvcResult created = send(manager, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"%s","name":"t"%s}""".formatted(product, number,
                template == null ? "" : ",\"templateCode\":\"" + template + "\""));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        return first(FIRST_VERSION_ID, body(created));
    }

    private UUID createProduct(UUID site, String code) throws Exception {
        MvcResult p = send(manager, post("/api/v1/products"), """
                {"siteId":"%s","code":"%s","name":"Metformin","productType":"DRUG_PRODUCT",
                 "expirationValue":24,"expirationUnit":"MONTH"}""".formatted(site, code));
        assertEquals(201, p.getResponse().getStatus(), body(p));
        return UUID.fromString(first(ID, body(p)));
    }

    private MvcResult approve(Actor who, String versionId, long version) throws Exception {
        SecurityTestSupport.grantSigningReauth(who.session(), tenant, who.id(), "PROTOCOL_VERSION",
                UUID.fromString(versionId), version);
        return send(who, post("/api/v1/protocol-versions/" + versionId + "/approve"), "{\"expectedVersion\":" + version + "}");
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
                    username, username + "@prt.test", name, roles));
            users.linkInvitation(users.findById(uid).orElseThrow(), SecurityTestSupport.ISSUER, subject);
            return uid;
        }));
        OAuth2AuthenticationToken auth = SecurityTestSupport.authentication(tenant, "prt", id, subject, username,
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
