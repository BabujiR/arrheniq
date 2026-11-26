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
 * Controlled lists and the ICH starter set over the real HTTP API, security and database (URS-MD-006, MD-007):
 * every new tenant has the ICH conditions and time-point templates; values are added, changed, retired and
 * reactivated with an audit trail; values from the catalog or in use are never deleted; site values follow site
 * scope.
 */
@Requirement({"URS-MD-006", "URS-MD-007", "URS-AUD-001"})
class DictionaryIT extends PostgresIntegrationTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    private static final Pattern VERSION = Pattern.compile("\"version\":(\\d+)");

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
    private UUID site2;
    private Actor manager;      // STABILITY_MANAGER at S1 (dictionary.manage, test.manage)
    private Actor managerS2;    // STABILITY_MANAGER at S2 only
    private Actor approver;     // APPROVER_QA at S1 (no dictionary.manage)

    private record Actor(UUID id, OAuth2AuthenticationToken auth, MockHttpSession session, Cookie xsrf) { }

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tenant = provisionTenant("dict");
        site2 = addSite("S2", "Second site");
        manager = actor("mgr", "Sam Manager", "STABILITY_MANAGER", "S1");
        managerS2 = actor("mgr2", "Max Manager", "STABILITY_MANAGER", "S2");
        approver = actor("qa", "Quinn Approver", "APPROVER_QA", "S1");
    }

    @Test
    @Requirement("URS-MD-007")
    void aNewTenantHasTheIchConditionsAndTimePointTemplates() throws Exception {
        String lists = body(call(approver, get("/api/v1/dictionaries")));
        assertTrue(lists.contains("\"code\":\"STORAGE_CONDITION\"") && lists.contains("\"code\":\"MEASUREMENT\""),
                lists);

        String conditions = body(call(approver, get("/api/v1/dictionaries/STORAGE_CONDITION/values")));
        for (String ich : List.of("25C60RH", "30C65RH", "30C75RH", "40C75RH", "5C", "M20C", "PHOTO_Q1B")) {
            assertTrue(conditions.contains("\"code\":\"" + ich + "\""), ich + " missing: " + conditions);
        }
        assertTrue(conditions.contains("\"fromCatalog\":true") && conditions.contains("\"temp_c\": 25"), conditions);

        String templates = body(call(approver, get("/api/v1/protocol-templates")));
        assertTrue(templates.contains("\"code\":\"LT_ZONE_II_36M\"")
                && templates.contains("\"timepoints\":[0,3,6,9,12,18,24,36]")
                && templates.contains("\"code\":\"ACC_40_75_6M\""), templates);
        assertTrue(templates.contains("\"conditionLabel\":\"25 °C"), "labels come from the tenant's list: " + templates);
    }

    @Test
    void valuesAreAddedChangedRetiredAndReactivatedWithAnAuditTrail() throws Exception {
        assertEquals(403, send(approver, post("/api/v1/dictionaries/MEASUREMENT/values"), unit("MG/ML"))
                .getResponse().getStatus(), "QA approvers don't maintain lists");

        MvcResult created = send(manager, post("/api/v1/dictionaries/MEASUREMENT/values"), unit("mg/ml"));
        assertEquals(201, created.getResponse().getStatus(), body(created));
        assertTrue(body(created).contains("\"code\":\"MG/ML\"") && body(created).contains("\"inUse\":false"),
                "codes are upper-cased: " + body(created));
        String id = first(ID, body(created));

        assertEquals(409, send(manager, post("/api/v1/dictionaries/MEASUREMENT/values"), unit("MG/ML"))
                .getResponse().getStatus(), "duplicate code");

        MvcResult changed = send(manager, put("/api/v1/dictionaries/MEASUREMENT/values/" + id), """
                {"expectedVersion":0,"data":{"label":"Milligram per millilitre","sortOrder":5}}""");
        assertEquals(200, changed.getResponse().getStatus(), body(changed));
        assertEquals("1", first(VERSION, body(changed)));

        assertEquals(422, send(manager, post("/api/v1/dictionaries/MEASUREMENT/values/" + id + "/retire"),
                "{\"expectedVersion\":1}").getResponse().getStatus(), "retiring needs a reason");
        MvcResult retired = send(manager, post("/api/v1/dictionaries/MEASUREMENT/values/" + id + "/retire"),
                "{\"expectedVersion\":1,\"reason\":\"Use MG/ML-SOL instead\"}");
        assertTrue(body(retired).contains("\"status\":\"RETIRED\""), body(retired));
        MvcResult back = send(manager, post("/api/v1/dictionaries/MEASUREMENT/values/" + id + "/reactivate"),
                "{\"expectedVersion\":2,\"reason\":\"Still needed for site NJ\"}");
        assertTrue(body(back).contains("\"status\":\"ACTIVE\""), body(back));

        assertEquals(List.of("CREATE", "UPDATE", "RETIRE", "REACTIVATE"),
                auditTrail(UUID.fromString(id)));
    }

    @Test
    void aValueInUseIsRetiredNotDeletedAndChangingItNeedsAReason() throws Exception {
        String id = first(ID, body(send(manager, post("/api/v1/dictionaries/MEASUREMENT/values"), unit("UG"))));
        assertEquals(201, send(manager, post("/api/v1/test-methods"), """
                {"code":"IMP-UG","name":"Impurity","resultsFormat":"NUMERIC","unitCode":"UG","replicates":1}""")
                .getResponse().getStatus());

        String values = body(call(manager, get("/api/v1/dictionaries/MEASUREMENT/values")));
        Matcher ug = Pattern.compile("\"code\":\"UG\".*?\"inUse\":(true|false)").matcher(values);
        assertTrue(ug.find() && "true".equals(ug.group(1)), values);

        MvcResult delete = send(manager, post("/api/v1/dictionaries/MEASUREMENT/values/" + id + "/delete"),
                "{\"expectedVersion\":0,\"reason\":\"typo\"}");
        assertEquals(409, delete.getResponse().getStatus(), body(delete));
        assertTrue(body(delete).contains("ARQ-MD-011"), body(delete));

        String edit = "{\"expectedVersion\":0,%s\"data\":{\"label\":\"Microgram (µg)\"}}";
        assertEquals(422, send(manager, put("/api/v1/dictionaries/MEASUREMENT/values/" + id), edit.formatted(""))
                .getResponse().getStatus(), "a value in use is changed only with a reason");
        assertEquals(200, send(manager, put("/api/v1/dictionaries/MEASUREMENT/values/" + id),
                edit.formatted("\"reason\":\"Show the symbol\",")).getResponse().getStatus());
        MvcResult retired = send(manager, post("/api/v1/dictionaries/MEASUREMENT/values/" + id + "/retire"),
                "{\"expectedVersion\":1,\"reason\":\"Replaced by MCG\"}");
        assertEquals(200, retired.getResponse().getStatus(), body(retired));
    }

    @Test
    void catalogValuesAreNeverDeletedButMistakesAre() throws Exception {
        String conditions = body(call(manager, get("/api/v1/dictionaries/STORAGE_CONDITION/values")));
        Matcher ich = Pattern.compile("\"id\":\"([0-9a-f-]{36})\",\"version\":(\\d+),\"dictionaryCode\":"
                + "\"STORAGE_CONDITION\",\"code\":\"25C60RH\"").matcher(conditions);
        assertTrue(ich.find(), conditions);
        MvcResult refused = send(manager, post("/api/v1/dictionaries/STORAGE_CONDITION/values/" + ich.group(1)
                + "/delete"), "{\"expectedVersion\":" + ich.group(2) + ",\"reason\":\"not used here\"}");
        assertEquals(409, refused.getResponse().getStatus(), body(refused));
        assertTrue(body(refused).contains("ARQ-MD-010"), body(refused));

        String mistake = first(ID, body(send(manager, post("/api/v1/dictionaries/STORAGE_CONDITION/values"), """
                {"code":"25C6ORH","label":"typo","attributes":{"temp_c":25,"rh":60.5}}""")));
        assertEquals(422, send(manager, post("/api/v1/dictionaries/STORAGE_CONDITION/values/" + mistake + "/delete"),
                "{\"expectedVersion\":0}").getResponse().getStatus(), "deleting needs a reason");
        assertEquals(204, send(manager, post("/api/v1/dictionaries/STORAGE_CONDITION/values/" + mistake + "/delete"),
                "{\"expectedVersion\":0,\"reason\":\"Typo in the code (O instead of 0)\"}").getResponse().getStatus());
        assertFalse(body(call(manager, get("/api/v1/dictionaries/STORAGE_CONDITION/values"))).contains("25C6ORH"));
        assertEquals(List.of("CREATE", "LOGICAL_DELETE"), auditTrail(UUID.fromString(mistake)));
    }

    @Test
    void siteValuesAreVisibleAndEditableOnlyAtTheirSite() throws Exception {
        MvcResult created = send(managerS2, post("/api/v1/dictionaries/STORAGE_LOCATION/values"), """
                {"code":"CH-2","label":"Chamber 2 (25/60)","siteId":"%s"}""".formatted(site2));
        assertEquals(201, created.getResponse().getStatus(), body(created));

        assertTrue(body(call(managerS2, get("/api/v1/dictionaries/STORAGE_LOCATION/values"))).contains("CH-2"));
        assertFalse(body(call(manager, get("/api/v1/dictionaries/STORAGE_LOCATION/values"))).contains("CH-2"),
                "a value of site S2 is invisible at S1");
        assertEquals(403, send(manager, post("/api/v1/dictionaries/STORAGE_LOCATION/values"), """
                {"code":"CH-9","label":"Chamber 9","siteId":"%s"}""".formatted(site2)).getResponse().getStatus(),
                "managing S1 doesn't allow adding values for S2");
    }

    @Test
    void attributesAreValidated() throws Exception {
        MvcResult bad = send(manager, post("/api/v1/dictionaries/STORAGE_CONDITION/values"), """
                {"code":"X1","label":"x","attributes":{"Temp C":25,"nested":{"a":1}}}""");
        assertEquals(400, bad.getResponse().getStatus(), body(bad));
        assertTrue(body(bad).contains("attributes.Temp C") && body(bad).contains("attributes.nested"), body(bad));
    }

    /** Every column the in-use check reads exists (so a renamed column can't silently make values deletable). */
    @Test
    void everyUsageColumnExists() throws SQLException {
        try (Connection c = ownerConnection();
             PreparedStatement q = c.prepareStatement("""
                     SELECT count(*) FROM information_schema.columns
                      WHERE table_schema = ? AND table_name = ? AND column_name = ?""")) {
            for (List<DictionaryUsage.Column> columns : DictionaryUsage.COLUMNS.values()) {
                for (DictionaryUsage.Column col : columns) {
                    String[] t = col.table().split("\\.");
                    q.setString(1, t[0]);
                    q.setString(2, t[1]);
                    q.setString(3, col.column());
                    try (ResultSet rs = q.executeQuery()) {
                        rs.next();
                        assertEquals(1, rs.getInt(1), col.table() + "." + col.column() + " does not exist");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String unit(String code) {
        return "{\"code\":\"" + code + "\",\"label\":\"" + code.toLowerCase() + "\"}";
    }

    private MvcResult send(Actor who, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json)
                .cookie(who.xsrf()).header("X-XSRF-TOKEN", who.xsrf().getValue())
                .with(authentication(who.auth())).session(who.session())).andReturn();
    }

    private MvcResult call(Actor who, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(authentication(who.auth())).session(who.session())).andReturn();
    }

    private Actor actor(String username, String name, String role, String site) throws Exception {
        String subject = "sub-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID id = TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(_ -> {
            audit.open("it.invite");
            UUID uid = invitations.invite(AuditActor.system("it"), new UserInvitationService.Invitation(
                    username, username + "@dict.test", name,
                    List.of(new UserInvitationService.RoleAssignment(role, site))));
            users.linkInvitation(users.findById(uid).orElseThrow(), SecurityTestSupport.ISSUER, subject);
            return uid;
        }));
        OAuth2AuthenticationToken auth = SecurityTestSupport.authentication(tenant, "dict", id, subject, username,
                name);
        MockHttpSession session = SecurityTestSupport.activeSession();
        Cookie xsrf = mvc.perform(get("/api/v1/me").with(authentication(auth)).session(session))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "CSRF cookie");
        return new Actor(id, auth, session, xsrf);
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

    private List<String> auditTrail(UUID recordId) throws SQLException {
        try (Connection c = tenantOwnerConnection();
             PreparedStatement q = c.prepareStatement("SELECT action FROM audit.audit_event"
                     + " WHERE record_type = 'DICTIONARY_VALUE' AND record_id = ? ORDER BY seq")) {
            q.setObject(1, recordId);
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
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);   // labels contain °, µ
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        assertTrue(m.find(), "no " + p + " in " + s);
        return m.group(1);
    }
}
