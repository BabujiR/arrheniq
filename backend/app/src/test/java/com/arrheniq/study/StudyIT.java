package com.arrheniq.study;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Study login over the real HTTP API, security and database (URS-STU-001/002/004/005, URS-SCH-001/002): one login,
 * one study per condition with its own study ID; approval by e-signature generates the schedule in the same
 * transaction; an approved study without a schedule can't be committed; IDs stay unique under parallel logins.
 */
@Requirement({"URS-STU-001", "URS-STU-002", "URS-STU-004", "URS-STU-005", "URS-SCH-001", "URS-SCH-002",
        "URS-ESG-001", "URS-ESG-002"})
class StudyIT extends PostgresIntegrationTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern FIRST_VERSION_ID = Pattern.compile("\"versions\":\\[\\{\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern STUDY_CODE = Pattern.compile("\"studyCode\":\"([^\"]+)\"");

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
    private Actor manager;      // STABILITY_MANAGER at S1 (study.create)
    private Actor approver;     // APPROVER_QA at S1 (study.approve)
    private Actor both;
    private Actor managerS2;
    private UUID product;
    private String specVersion;
    private String longTerm;    // approved protocol version, 25C60RH, 0..36 months
    private String accelerated; // approved protocol version, 40C75RH, 0..6 months

    private record Actor(UUID id, OAuth2AuthenticationToken auth, MockHttpSession session, Cookie xsrf) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("stu");
        UUID site1 = siteId("S1");
        addSite("S2", "Second site");
        manager = actor("mgr", "Sam Manager", List.of(role("STABILITY_MANAGER", "S1")));
        approver = actor("qa", "Quinn Approver", List.of(role("APPROVER_QA", "S1")));
        both = actor("both", "Bo Both", List.of(role("STABILITY_MANAGER", "S1"), role("APPROVER_QA", "S1")));
        managerS2 = actor("mgr2", "Max Manager", List.of(role("STABILITY_MANAGER", "S2")));
        // master data approved without signatures here (approval.* = NONE); studies keep SINGLE approval
        ownerSql("""
                UPDATE app.tenant_setting SET value = '"NONE"'
                 WHERE setting_key IN ('approval.product', 'approval.specification', 'approval.protocol')""");
        ownerSql("""
                INSERT INTO app.dictionary_value (tenant_id, id, dictionary_id, code, label)
                SELECT app.current_tenant(), gen_random_uuid(), d.id, v.code, v.label
                  FROM app.dictionary d, (VALUES ('CH-01', 'Chamber 1 (25/60)'), ('CH-02', 'Chamber 2 (40/75)')) v(code, label)
                 WHERE d.code = 'STORAGE_LOCATION'""");
        String assay = created(send(manager, post("/api/v1/test-methods"), """
                {"code":"ASSAY","name":"Assay (HPLC)","resultsFormat":"NUMERIC","unitCode":"%LC","replicates":2}"""));
        product = UUID.fromString(created(send(manager, post("/api/v1/products"), """
                {"siteId":"%s","code":"MET-500","name":"Metformin","productType":"DRUG_PRODUCT",
                 "expirationValue":24,"expirationUnit":"MONTH"}""".formatted(site1))));
        ok(send(manager, post("/api/v1/products/" + product + "/submit"), "{\"expectedVersion\":0}"));
        MvcResult spec = send(manager, post("/api/v1/specifications"), """
                {"productId":"%s","specNumber":"SPEC-MET-500","title":"Stability"}""".formatted(product));
        assertEquals(201, spec.getResponse().getStatus(), body(spec));
        specVersion = first(FIRST_VERSION_ID, body(spec));
        ok(send(manager, put("/api/v1/spec-versions/" + specVersion), """
                {"expectedVersion":0,"data":{"tests":[{"testMethodId":"%s","lowerLimit":95,"upperLimit":105}]}}"""
                .formatted(assay)));
        ok(send(manager, post("/api/v1/spec-versions/" + specVersion + "/submit"), "{\"expectedVersion\":1}"));
        longTerm = approvedProtocol("PRT-LT", "LT_ZONE_II_36M");
        accelerated = approvedProtocol("PRT-ACC", "ACC_40_75_6M");
    }

    @Test
    void oneLoginCreatesAStudyPerConditionAndApprovalGeneratesTheSchedule() throws Exception {
        LocalDate start = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7);
        MvcResult login = send(manager, post("/api/v1/studies"), loginJson(start, longTerm, "CH-01", accelerated,
                "CH-02"));
        assertEquals(201, login.getResponse().getStatus(), body(login));
        List<String> codes = all(STUDY_CODE, body(login));
        String yy = String.format("%02d", LocalDate.now(ZoneId.of("America/New_York")).getYear() % 100);
        assertEquals(List.of(yy + "0001", yy + "0002"), codes, "YYnnnn, one per condition: " + body(login));
        assertTrue(body(login).contains("\"conditionCode\":\"25C60RH\"")
                && body(login).contains("\"conditionCode\":\"40C75RH\"") && body(login).contains("\"status\":\"DRAFT\""),
                body(login));
        String study = all(ID, body(login)).getFirst();

        String details = body(call(manager, get("/api/v1/studies/" + study)));
        assertTrue(details.contains("\"siblings\":[{") && details.contains("\"storageLocationCode\":\"CH-01\"")
                && details.contains("\"reasonLabel\":"), details);
        String none = body(call(manager, get("/api/v1/studies/" + study + "/schedule")));
        assertTrue(none.contains("\"timepoints\":[]") && none.contains("\"algorithm\":null"),
                "no schedule before approval: " + none);

        ok(send(manager, post("/api/v1/studies/" + study + "/submit"), "{\"expectedVersion\":0}"));
        assertEquals(403, approve(manager, study, 1).getResponse().getStatus(), "a manager can't approve");
        MvcResult approved = approve(approver, study, 1);
        assertEquals(200, approved.getResponse().getStatus(), body(approved));
        assertTrue(body(approved).contains("\"status\":\"APPROVED\"")
                && body(approved).contains("\"scheduleAlgorithm\":\"schedule.pull-dates@1.0.0\""), body(approved));

        String schedule = body(call(manager, get("/api/v1/studies/" + study + "/schedule")));
        assertEquals(8, count(schedule, "\"status\":\"SCHEDULED\""), "0 3 6 9 12 18 24 36 months: " + schedule);
        assertTrue(schedule.contains("\"label\":\"Initial\"") && schedule.contains("\"label\":\"36M\"")
                && schedule.contains("\"calculatedDate\":\"" + start.plusMonths(36) + "\""), schedule);
        List<String> trail = auditTrail("STUDY", UUID.fromString(study));
        assertTrue(trail.getLast().startsWith("SCHEDULE_GENERATE") && trail.getLast().contains("tp.36M.pullDate"),
                "schedule generation is audited: " + trail);
        assertTrue(body(call(manager, get("/api/v1/studies?productId=" + product))).contains("\"nextPullDate\":"),
                "list shows the next pull");
    }

    @Test
    void loginMistakesAreRejected() throws Exception {
        LocalDate start = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7);
        String draftProtocol = first(FIRST_VERSION_ID, body(send(manager, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"PRT-DRAFT","name":"draft","templateCode":"INT_30_65_12M"}"""
                .formatted(product))));
        MvcResult bad = send(manager, post("/api/v1/studies"), loginJson(start, longTerm, "CH-99", draftProtocol,
                "CH-02"));
        assertEquals(400, bad.getResponse().getStatus(), body(bad));
        assertTrue(body(bad).contains("conditions[0].storageLocationCode")
                && body(bad).contains("conditions[1].protocolVersionId") && body(bad).contains("NOT_APPROVED"), body(bad));
        MvcResult twice = send(manager, post("/api/v1/studies"), loginJson(start, longTerm, "CH-01", longTerm, "CH-02"));
        assertEquals(400, twice.getResponse().getStatus(), body(twice));
        assertTrue(body(twice).contains("DUPLICATE"), body(twice));
        MvcResult noLot = send(manager, post("/api/v1/studies"), loginJson(start, longTerm, "CH-01", null, null)
                .replace("\"LOT-001\"", "\" \""));
        assertEquals(400, noLot.getResponse().getStatus());
        assertTrue(body(noLot).contains("lotNumber"), body(noLot));
    }

    @Test
    void segregationOfDutiesBackEntryAndSiteScope() throws Exception {
        // the person who logged in and submitted can't approve
        LocalDate start = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7);
        String mine = all(ID, body(send(both, post("/api/v1/studies"), loginJson(start, accelerated, "CH-02", null,
                null)))).getFirst();
        ok(send(both, post("/api/v1/studies/" + mine + "/submit"), "{\"expectedVersion\":0}"));
        MvcResult sod = approve(both, mine, 1);
        assertEquals(403, sod.getResponse().getStatus(), body(sod));
        assertTrue(body(sod).contains("ARQ-SOD-001"), body(sod));
        assertEquals(404, call(managerS2, get("/api/v1/studies/" + mine)).getResponse().getStatus());
        assertEquals(404, call(managerS2, get("/api/v1/studies/" + mine + "/schedule")).getResponse().getStatus());

        // a study that started 100 days ago already has past pull dates: back-entry isn't available yet
        String old = all(ID, body(send(manager, post("/api/v1/studies"), loginJson(start.minusDays(107), accelerated,
                "CH-02", null, null)))).getFirst();
        ok(send(manager, post("/api/v1/studies/" + old + "/submit"), "{\"expectedVersion\":0}"));
        MvcResult back = approve(approver, old, 1);
        assertEquals(400, back.getResponse().getStatus(), body(back));
        assertTrue(body(back).contains("BACK_ENTRY"), body(back));
        String after = body(call(manager, get("/api/v1/studies/" + old)));
        assertTrue(after.contains("\"status\":\"IN_REVIEW\"") && after.contains("\"signatures\":[]"),
                "rolled back with the signature: " + after);
    }

    @Test
    void anApprovedStudyWithoutAScheduleCannotBeCommitted() throws Exception {
        LocalDate start = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7);
        String study = all(ID, body(send(manager, post("/api/v1/studies"), loginJson(start, accelerated, "CH-02", null,
                null)))).getFirst();
        SQLException e = assertThrows(SQLException.class,
                () -> ownerSql("UPDATE app.study SET status = 'APPROVED' WHERE id = '" + study + "'"));
        assertTrue(e.getMessage().contains("ARQ-STU-001"), e.getMessage());
    }

    @Test
    void parallelLoginsGetUniqueStudyIds() throws Exception {
        LocalDate start = LocalDate.now(ZoneId.of("America/New_York")).plusDays(7);
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String json = loginJson(start, accelerated, "CH-02", null, null).replace("LOT-001", "LOT-P" + i);
                results.add(pool.submit(() -> send(manager, post("/api/v1/studies"), json)));
            }
            Set<String> codes = new HashSet<>();
            for (Future<MvcResult> f : results) {
                MvcResult r = f.get();
                assertEquals(201, r.getResponse().getStatus(), body(r));
                codes.addAll(all(STUDY_CODE, body(r)));
            }
            assertEquals(n, codes.size(), "unique study IDs: " + codes);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ helpers

    private String loginJson(LocalDate start, String protocol1, String location1, @Nullable String protocol2,
                                    @Nullable String location2) {
        String second = protocol2 == null ? "" : ",{\"protocolVersionId\":\"%s\",\"storageLocationCode\":\"%s\"}"
                .formatted(protocol2, location2);
        return """
                {"productId":"%s","specVersionId":"%s","lotNumber":"LOT-001","manufactureDate":"%s",
                 "onStabilityDate":"%s","reasonCode":"REGISTRATION","studyLengthCode":"36M",
                 "conditions":[{"protocolVersionId":"%s","storageLocationCode":"%s"}%s]}"""
                .formatted(product, specVersion, start.minusDays(30), start, protocol1, location1, second);
    }

    private String approvedProtocol(String number, String template) throws Exception {
        MvcResult created = send(manager, post("/api/v1/protocols"), """
                {"productId":"%s","protocolNumber":"%s","name":"%s","templateCode":"%s","specVersionId":"%s"}"""
                .formatted(product, number, number, template, specVersion));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        String version = first(FIRST_VERSION_ID, body(created));
        MvcResult submitted = send(manager, post("/api/v1/protocol-versions/" + version + "/submit"),
                "{\"expectedVersion\":0}");
        assertTrue(body(submitted).contains("\"status\":\"APPROVED\""), body(submitted));
        return version;
    }

    private MvcResult approve(Actor who, String studyId, long version) throws Exception {
        SecurityTestSupport.grantSigningReauth(who.session(), tenant, who.id(), "STUDY", UUID.fromString(studyId),
                version);
        return send(who, post("/api/v1/studies/" + studyId + "/approve"), "{\"expectedVersion\":" + version + "}");
    }

    private static String created(MvcResult r) throws Exception {
        assertEquals(201, r.getResponse().getStatus(), body(r));
        return first(ID, body(r));
    }

    private static void ok(MvcResult r) throws Exception {
        assertEquals(200, r.getResponse().getStatus(), body(r));
    }

    private static List<String> all(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static int count(String s, String what) {
        return s.split(Pattern.quote(what), -1).length - 1;
    }

    private void ownerSql(String sql) throws SQLException {
        try (Connection c = tenantOwnerConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.executeUpdate();
            c.commit();
        }
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
                    username, username + "@stu.test", name, roles));
            users.linkInvitation(users.findById(uid).orElseThrow(), SecurityTestSupport.ISSUER, subject);
            return uid;
        }));
        OAuth2AuthenticationToken auth = SecurityTestSupport.authentication(tenant, "stu", id, subject, username,
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
