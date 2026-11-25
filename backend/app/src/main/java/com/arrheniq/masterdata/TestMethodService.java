package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.audit.FieldDiff;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.RequirePermission;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Test library (URS-MD-001). Tenant-wide (not site-scoped). Read: {@code masterdata.view};
 * write: {@code test.manage}. Tests are retired, never deleted (URS-AUD-005).
 */
@Service
public class TestMethodService {

    static final String RECORD_TYPE = "TEST_METHOD";

    private static final String COLUMNS = """
            id, version, code, name, method_reference, lab_number, results_format, unit_code, replicates, decimals,
            default_lower_limit, default_upper_limit, default_text_limit, default_alert_lower, default_alert_upper,
            default_variance_lower_pct, default_variance_upper_pct, trendable, status, created_at, updated_at""";

    private static final RowMapper<TestMethod> ROW = (rs, n) -> new TestMethod(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getString("code"), rs.getString("name"),
            rs.getString("method_reference"), rs.getString("lab_number"), rs.getString("results_format"),
            rs.getString("unit_code"), rs.getInt("replicates"), rs.getObject("decimals", Integer.class),
            rs.getBigDecimal("default_lower_limit"), rs.getBigDecimal("default_upper_limit"),
            rs.getString("default_text_limit"), rs.getBigDecimal("default_alert_lower"),
            rs.getBigDecimal("default_alert_upper"), rs.getBigDecimal("default_variance_lower_pct"),
            rs.getBigDecimal("default_variance_upper_pct"), rs.getBoolean("trendable"), rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            MasterDataSupport.instant(rs.getObject("updated_at", OffsetDateTime.class)));

    private final JdbcClient jdbc;
    private final AuditService audit;

    public TestMethodService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<TestMethod> list(@Nullable String status, @Nullable String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM app.test_method WHERE deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (code ILIKE ? ESCAPE '\\' OR name ILIKE ? ESCAPE '\\')");
            params.add(MasterDataSupport.likePattern(q));
            params.add(MasterDataSupport.likePattern(q));
        }
        sql.append(" ORDER BY code LIMIT ? OFFSET ?");
        params.add(MasterDataSupport.limit(limit));
        params.add(Math.max(0, offset));
        return jdbc.sql(sql.toString()).params(params).query(ROW).list();
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public TestMethod get(UUID id) {
        return find(id, false);
    }

    @RequirePermission("test.manage")
    @Transactional
    public TestMethod create(TestMethodInput input) {
        TestMethodInput.Clean c = TestMethodRules.validate(input);
        requireUniqueCode(c.code(), null);
        UUID id = UUID.randomUUID();
        audit.open("test_method.create");
        jdbc.sql("""
                INSERT INTO app.test_method (tenant_id, id, created_by, code, name, method_reference, lab_number,
                    results_format, unit_code, replicates, decimals, default_lower_limit, default_upper_limit,
                    default_text_limit, default_alert_lower, default_alert_upper, default_variance_lower_pct,
                    default_variance_upper_pct, trendable)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(id, CurrentUserHolder.require().userId(), c.code(), c.name(), c.methodReference(), c.labNumber(),
                    c.resultsFormat(), c.unitCode(), c.replicates(), c.decimals(), c.defaultLowerLimit(),
                    c.defaultUpperLimit(), c.defaultTextLimit(), c.defaultAlertLower(), c.defaultAlertUpper(),
                    c.defaultVarianceLowerPct(), c.defaultVarianceUpperPct(), c.trendable())
            .update();
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", RECORD_TYPE, id, 0L, c.code(), null,
                FieldDiff.between(null, TestMethod.auditMap(c, "ACTIVE")), null, null, null));
        return find(id, false);
    }

    @RequirePermission("test.manage")
    @Transactional
    public TestMethod update(UUID id, long expectedVersion, TestMethodInput input, @Nullable String reason) {
        TestMethod current = lockedCurrent(id, expectedVersion);
        if ("RETIRED".equals(current.status())) {
            throw ApiException.invalidState(current.status(), "EDIT");
        }
        TestMethodInput.Clean c = TestMethodRules.validate(input);
        List<FieldChange> changes = FieldDiff.between(current.auditMap(), TestMethod.auditMap(c, current.status()));
        if (changes.isEmpty()) {
            return current;
        }
        requireUniqueCode(c.code(), id);
        audit.open("test_method.update");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", RECORD_TYPE, id, current.version() + 1,
                c.code(), null, changes, current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.test_method SET code = ?, name = ?, method_reference = ?, lab_number = ?,
                    results_format = ?, unit_code = ?, replicates = ?, decimals = ?, default_lower_limit = ?,
                    default_upper_limit = ?, default_text_limit = ?, default_alert_lower = ?, default_alert_upper = ?,
                    default_variance_lower_pct = ?, default_variance_upper_pct = ?, trendable = ?,
                    version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(c.code(), c.name(), c.methodReference(), c.labNumber(), c.resultsFormat(), c.unitCode(),
                    c.replicates(), c.decimals(), c.defaultLowerLimit(), c.defaultUpperLimit(), c.defaultTextLimit(),
                    c.defaultAlertLower(), c.defaultAlertUpper(), c.defaultVarianceLowerPct(),
                    c.defaultVarianceUpperPct(), c.trendable(), CurrentUserHolder.require().userId(), id,
                    expectedVersion)
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return find(id, false);
    }

    /** Retired tests can't be added to new specifications; existing use is unaffected. Reason required. */
    @RequirePermission("test.manage")
    @Transactional
    public TestMethod retire(UUID id, long expectedVersion, @Nullable String reason) {
        TestMethod current = lockedCurrent(id, expectedVersion);
        if (!"ACTIVE".equals(current.status())) {
            throw ApiException.invalidState(current.status(), "RETIRE");
        }
        audit.open("test_method.retire");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("RETIRE", RECORD_TYPE, id, current.version() + 1,
                current.code(), null, List.of(new FieldChange("status", "ACTIVE", "RETIRED")), current.status(),
                reason, null));
        jdbc.sql("""
                UPDATE app.test_method SET status = 'RETIRED', version = version + 1,
                       updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(CurrentUserHolder.require().userId(), id, expectedVersion)
            .update();
        return find(id, false);
    }

    private TestMethod lockedCurrent(UUID id, long expectedVersion) {
        TestMethod current = find(id, true);
        if (current.version() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        return current;
    }

    private TestMethod find(UUID id, boolean forUpdate) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app.test_method WHERE id = ? AND deleted_at IS NULL"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param(id)
                .query(ROW)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Test"));
    }

    private void requireUniqueCode(String code, @Nullable UUID exceptId) {
        Boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.test_method WHERE code = ?"
                        + (exceptId == null ? ")" : " AND id <> ?)"))
                .params(exceptId == null ? List.of(code) : List.of(code, exceptId))
                .query(Boolean.class)
                .single();
        if (Boolean.TRUE.equals(taken)) {
            throw ApiException.duplicate("code", code);
        }
    }
}
