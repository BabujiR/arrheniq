package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.audit.FieldDiff;
import com.arrheniq.kernel.esign.ApprovalLevel;
import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.RecordHasher;
import com.arrheniq.kernel.esign.SignatureService;
import com.arrheniq.kernel.security.Access;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.ReauthEvidence;
import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.kernel.security.SiteScope;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Product specifications (URS-MD-003) with versions and approval (URS-MD-004). Site-scoped through the product:
 * read {@code masterdata.view}, edit {@code spec.manage}, approve/reject {@code spec.approve}, each at the
 * product's site. Only a DRAFT version is edited; an effective version never changes (see {@link SpecLifecycle}).
 */
@Service
public class SpecificationService {

    static final String SPEC_RECORD = "SPECIFICATION";
    static final String VERSION_RECORD = "SPEC_VERSION";
    /** Tenant setting {@code approval.specification}: NONE, SINGLE or DOUBLE. */
    static final String APPROVAL_KIND = "specification";

    private static final String SUMMARY_SELECT = """
            SELECT s.id, s.spec_number, s.title, s.product_id, p.code AS product_code, p.name AS product_name,
                   p.site_id, ev.version_no AS effective_no, ev.effective_from, ev.id AS effective_id,
                   ov.id AS open_id, ov.version_no AS open_no, ov.status AS open_status
              FROM app.specification s
              JOIN app.product p ON p.tenant_id = s.tenant_id AND p.id = s.product_id
              LEFT JOIN app.spec_version ev ON ev.tenant_id = s.tenant_id AND ev.specification_id = s.id
                                           AND ev.status = 'EFFECTIVE' AND ev.deleted_at IS NULL
              LEFT JOIN LATERAL (SELECT x.id, x.version_no, x.status FROM app.spec_version x
                                  WHERE x.tenant_id = s.tenant_id AND x.specification_id = s.id
                                    AND x.status IN ('DRAFT', 'IN_REVIEW') AND x.deleted_at IS NULL
                                  ORDER BY x.version_no DESC LIMIT 1) ov ON true
             WHERE s.deleted_at IS NULL""";

    private static final RowMapper<SpecificationSummary> SUMMARY = (rs, _) -> new SpecificationSummary(
            rs.getObject("id", UUID.class), rs.getString("spec_number"), rs.getString("title"),
            rs.getObject("product_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"),
            rs.getObject("site_id", UUID.class), rs.getObject("effective_no", Integer.class),
            rs.getObject("effective_from", LocalDate.class), rs.getObject("open_id", UUID.class),
            rs.getObject("open_no", Integer.class), rs.getString("open_status"),
            rs.getObject("effective_id", UUID.class));

    /** A version row (without tests). */
    private record VersionRow(UUID id, long version, UUID specificationId, int versionNo, String status,
                              @Nullable LocalDate effectiveFrom, @Nullable String changeSummary,
                              @Nullable UUID updatedBy, OffsetDateTime createdAt, @Nullable OffsetDateTime updatedAt) { }

    private static final RowMapper<VersionRow> VERSION = (rs, _) -> new VersionRow(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getObject("specification_id", UUID.class),
            rs.getInt("version_no"), rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
            rs.getString("change_summary"), rs.getObject("updated_by", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));

    private static final String VERSION_COLUMNS = """
            id, version, specification_id, version_no, status, effective_from, change_summary, updated_by,
            created_at, updated_at""";

    private static final RowMapper<SpecTest> TEST = (rs, _) -> new SpecTest(
            rs.getObject("test_method_id", UUID.class), rs.getString("test_code"), rs.getString("test_status"),
            rs.getInt("sort_order"), rs.getString("display_name"), rs.getString("results_format"),
            rs.getString("unit_code"), rs.getInt("replicates"), rs.getObject("decimals", Integer.class),
            rs.getBigDecimal("lower_limit"), rs.getBigDecimal("upper_limit"), rs.getString("text_limit"),
            rs.getBigDecimal("alert_lower"), rs.getBigDecimal("alert_upper"), rs.getBoolean("trendable"));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final SignatureService signatures;
    private final ApprovalService approvals;
    private final Access access;

    public SpecificationService(JdbcClient jdbc, AuditService audit, SignatureService signatures,
                                ApprovalService approvals, Access access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.signatures = signatures;
        this.approvals = approvals;
        this.access = access;
    }

    // ------------------------------------------------------------------ reading

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<SpecificationSummary> list(@Nullable UUID productId, @Nullable String q, int limit, int offset) {
        SiteScope scope = CurrentUserHolder.require().grants().scopeOf("masterdata.view");
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT);
        List<Object> params = new ArrayList<>();
        if (!scope.allSites()) {
            sql.append(" AND p.site_id = ANY (CAST(? AS uuid[]))");
            params.add(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (productId != null) {
            sql.append(" AND s.product_id = ?");
            params.add(productId);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (s.spec_number ILIKE ? ESCAPE '\\' OR s.title ILIKE ? ESCAPE '\\'"
                    + " OR p.code ILIKE ? ESCAPE '\\')");
            String like = MasterDataSupport.likePattern(q);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY s.spec_number LIMIT ? OFFSET ?");
        params.add(MasterDataSupport.limit(limit));
        params.add(Math.max(0, offset));
        return jdbc.sql(sql.toString()).params(params).query(SUMMARY).list();
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public SpecificationDetails get(UUID specificationId) {
        SpecificationSummary spec = visibleSpec(specificationId);
        List<SpecVersionSummary> versions = jdbc.sql("""
                SELECT v.id, v.version_no, v.status, v.effective_from, v.change_summary, v.created_at, v.updated_at,
                       (SELECT count(*) FROM app.spec_test t
                         WHERE t.tenant_id = v.tenant_id AND t.spec_version_id = v.id AND t.deleted_at IS NULL)
                           AS test_count
                  FROM app.spec_version v
                 WHERE v.specification_id = ? AND v.deleted_at IS NULL
                 ORDER BY v.version_no DESC
                """)
            .param(specificationId)
            .query((rs, _) -> new SpecVersionSummary(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                    rs.getString("status"), rs.getObject("effective_from", LocalDate.class),
                    rs.getString("change_summary"), rs.getInt("test_count"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    MasterDataSupport.instant(rs.getObject("updated_at", OffsetDateTime.class))))
            .list();
        return new SpecificationDetails(spec, versions);
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public SpecVersionDetails version(UUID versionId) {
        VersionRow v = versionRow(versionId, false);
        return details(v, visibleSpec(v.specificationId()));
    }

    // ------------------------------------------------------------------ creating and editing

    /** Creates the specification and its version 1 as an empty DRAFT. */
    @RequirePermission("spec.manage")
    @Transactional
    public SpecificationDetails create(@Nullable UUID productId, @Nullable String specNumber, @Nullable String title) {
        Validation v = new Validation();
        String number = Validation.normalizeCode(specNumber);
        v.code("specNumber", number);
        v.required("title", title, 200);
        if (productId == null) {
            v.add("productId", "REQUIRED", null);
        }
        v.throwIfAny();
        ProductRef product = product(Objects.requireNonNull(productId));
        access.requireAt("spec.manage", product.siteId());
        if ("RETIRED".equals(product.status())) {
            throw ApiException.invalidState(product.status(), "ADD_SPECIFICATION");
        }
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.specification WHERE spec_number = ?)")
                .param(number).query(Boolean.class).single();
        if (taken) {
            throw ApiException.duplicate("specNumber", number);
        }
        String cleanTitle = Objects.requireNonNull(Validation.trimToNull(title));
        UUID specId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID me = CurrentUserHolder.require().userId();
        audit.open("specification.create");
        jdbc.sql("""
                INSERT INTO app.specification (tenant_id, id, created_by, product_id, spec_number, title)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?)
                """)
            .params(specId, me, product.id(), number, cleanTitle)
            .update();
        jdbc.sql("""
                INSERT INTO app.spec_version (tenant_id, id, created_by, specification_id, version_no, status)
                VALUES (app.current_tenant(), ?, ?, ?, 1, 'DRAFT')
                """)
            .params(versionId, me, specId)
            .update();
        Map<String, @Nullable Object> header = new LinkedHashMap<>();
        header.put("productId", product.id());
        header.put("specNumber", number);
        header.put("title", cleanTitle);
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", SPEC_RECORD, specId, 0L, number,
                product.siteId(), FieldDiff.between(null, header), null, null, null));
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", VERSION_RECORD, versionId, 0L,
                number + " v1", product.siteId(), List.of(new FieldChange("versionNo", null, 1L),
                        new FieldChange("status", null, "DRAFT")), null, null, null));
        return get(specId);
    }

    /**
     * Saves a DRAFT: change summary and the complete, ordered list of tests with their limits. Tests left out
     * are removed from the draft. The audit trail records each changed limit as {@code test.<CODE>.<field>}.
     */
    @RequirePermission("spec.manage")
    @Transactional
    public SpecVersionDetails saveDraft(UUID versionId, long expectedVersion, @Nullable String changeSummary,
                                        List<SpecTestInput> tests) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.manage", spec.siteId());
        SpecLifecycle.next(current.status(), SpecLifecycle.Action.EDIT);

        Validation v = new Validation();
        v.maxLength("changeSummary", changeSummary, 2000);
        v.throwIfAny();
        List<SpecTest> after = SpecRules.validate(tests, library(tests), carriedOver(current));
        List<SpecTest> before = tests(versionId);
        Map<String, @Nullable Object> beforeMap = content(current.changeSummary(), before);
        Map<String, @Nullable Object> afterMap = content(Validation.trimToNull(changeSummary), after);
        List<FieldChange> changes = FieldDiff.between(beforeMap, afterMap);
        if (changes.isEmpty()) {
            return details(current, spec);
        }
        UUID me = CurrentUserHolder.require().userId();
        audit.open("spec_version.update");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", VERSION_RECORD, versionId,
                current.version() + 1, key(spec, current), spec.siteId(), changes, current.status(), null, null));
        writeTests(versionId, after, me);
        int n = jdbc.sql("""
                UPDATE app.spec_version SET change_summary = ?, version = version + 1,
                       updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            // as a list: the change summary may be NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(Validation.trimToNull(changeSummary), me, versionId, expectedVersion))
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return version(versionId);
    }

    /**
     * Starts the next version as a DRAFT copy of the effective one (or of the latest, if none is effective).
     * Only one version can be open (DRAFT or IN_REVIEW) at a time.
     */
    @RequirePermission("spec.manage")
    @Transactional
    public SpecVersionDetails newVersion(UUID specificationId) {
        // lock first, then read: serialises concurrent "new version" clicks
        jdbc.sql("SELECT id FROM app.specification WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(specificationId).query(UUID.class).optional();
        SpecificationSummary spec = visibleSpec(specificationId);
        access.requireAt("spec.manage", spec.siteId());
        if (spec.openVersionId() != null) {
            throw new ApiException(409, "ARQ-MD-020", "Version " + spec.openVersionNo() + " is still "
                    + Objects.requireNonNull(spec.openStatus()).toLowerCase(Locale.ROOT).replace('_', ' ')
                    + "; finish or discard it first", List.of());
        }
        VersionRow base = jdbc.sql("SELECT " + VERSION_COLUMNS + """
                  FROM app.spec_version
                 WHERE specification_id = ? AND deleted_at IS NULL
                 ORDER BY (status = 'EFFECTIVE') DESC, version_no DESC
                 LIMIT 1""")
            .param(specificationId).query(VERSION).single();
        int next = jdbc.sql("SELECT max(version_no) + 1 FROM app.spec_version WHERE specification_id = ?")
                .param(specificationId).query(Integer.class).single();
        UUID versionId = UUID.randomUUID();
        UUID me = CurrentUserHolder.require().userId();
        List<SpecTest> copied = tests(base.id());
        audit.open("spec_version.create");
        jdbc.sql("""
                INSERT INTO app.spec_version (tenant_id, id, created_by, specification_id, version_no, status)
                VALUES (app.current_tenant(), ?, ?, ?, ?, 'DRAFT')
                """)
            .params(versionId, me, specificationId, next)
            .update();
        writeTests(versionId, copied, me);
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", VERSION_RECORD, versionId, 0L,
                spec.specNumber() + " v" + next, spec.siteId(), List.of(new FieldChange("versionNo", null, (long) next),
                        new FieldChange("copiedFrom", null, "v" + base.versionNo()),
                        new FieldChange("status", null, "DRAFT")), null, null, null));
        return version(versionId);
    }

    // ------------------------------------------------------------------ workflow

    /**
     * Submits a DRAFT for approval. With {@code approval.specification = NONE} it becomes effective at once,
     * without a signature; the audit trail records why.
     */
    @RequirePermission("spec.manage")
    @Transactional
    public SpecVersionDetails submit(UUID versionId, long expectedVersion) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.manage", spec.siteId());
        String next = SpecLifecycle.next(current.status(), SpecLifecycle.Action.SUBMIT);
        if (tests(versionId).isEmpty()) {
            Validation v = new Validation();
            v.add("tests", "REQUIRED", "add at least one test before submitting");
            v.throwIfAny();
        }
        audit.open("spec_version.submit");
        VersionRow submitted = setStatus(spec, current, next, "SUBMIT", null);
        if (approvals.levelFor(APPROVAL_KIND, spec.siteId()) == ApprovalLevel.NONE) {
            makeEffective(spec, submitted, "No approval required (tenant setting approval.specification = NONE)");
        }
        return version(versionId);
    }

    /**
     * Approves with an electronic signature bound to the reviewed content and version (Part 11 §11.70); with
     * DOUBLE approval the first signature means REVIEWED. When complete, this version becomes EFFECTIVE today and
     * the previously effective version SUPERSEDED. Author and submitter can't sign (segregation of duties).
     */
    @RequirePermission("spec.approve")
    @Transactional
    public SpecVersionDetails approve(UUID versionId, long expectedVersion, @Nullable String comment,
                                      Supplier<ReauthEvidence> reauthentication) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.approve", spec.siteId());
        SpecLifecycle.next(current.status(), SpecLifecycle.Action.APPROVE);
        Set<UUID> excluded = new HashSet<>();
        UUID submitter = current.updatedBy();
        if (submitter != null) {
            excluded.add(submitter);
        }
        UUID editor = lastContentEditor(versionId);
        if (editor != null) {
            excluded.add(editor);
        }
        audit.open("spec_version.approve");
        ApprovalService.Step step = approvals.approve(new ApprovalService.Approvable(VERSION_RECORD, APPROVAL_KIND,
                versionId, current.version(), key(spec, current), spec.siteId(), signedContent(spec, current),
                excluded), comment, reauthentication);
        if (step.complete()) {
            makeEffective(spec, current, null);
        }
        return version(versionId);
    }

    /** Back to DRAFT with a reason; a first (REVIEWED) signature of a double approval no longer counts. */
    @RequirePermission("spec.approve")
    @Transactional
    public SpecVersionDetails reject(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.approve", spec.siteId());
        String next = SpecLifecycle.next(current.status(), SpecLifecycle.Action.REJECT);
        audit.open("spec_version.reject");
        VersionRow rejected = setStatus(spec, current, next, "REJECT", reason);
        int superseded = signatures.supersedeAll(VERSION_RECORD, versionId,
                "Rejected: " + Objects.requireNonNull(reason));
        if (superseded > 0) {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("SIGNATURE_SUPERSEDE", VERSION_RECORD,
                    versionId, rejected.version(), key(spec, current), spec.siteId(),
                    List.of(new FieldChange("supersededSignatures", null, (long) superseded)), next, reason, null));
        }
        return version(versionId);
    }

    /** Discards a DRAFT (logically deleted, with a reason). Discarding version 1 discards the specification. */
    @RequirePermission("spec.manage")
    @Transactional
    public void discard(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.manage", spec.siteId());
        SpecLifecycle.next(current.status(), SpecLifecycle.Action.DISCARD);
        UUID me = CurrentUserHolder.require().userId();
        audit.open("spec_version.discard");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("LOGICAL_DELETE", VERSION_RECORD, versionId,
                current.version() + 1, key(spec, current), spec.siteId(),
                List.of(new FieldChange("deleted", false, true)), current.status(), reason, null));
        String why = reason == null ? "" : reason;
        jdbc.sql("""
                UPDATE app.spec_version SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """)
            .params(me, why, versionId, expectedVersion)
            .update();
        jdbc.sql("""
                UPDATE app.spec_test SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?
                 WHERE spec_version_id = ? AND deleted_at IS NULL
                """)
            .params(me, why, versionId)
            .update();
        boolean anyLeft = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM app.spec_version WHERE specification_id = ? AND deleted_at IS NULL)
                """)
            .param(spec.id()).query(Boolean.class).single();
        if (!anyLeft) {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("LOGICAL_DELETE", SPEC_RECORD, spec.id(),
                    null, spec.specNumber(), spec.siteId(), List.of(new FieldChange("deleted", false, true)), null,
                    reason, null));
            jdbc.sql("""
                    UPDATE app.specification SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                           version = version + 1
                     WHERE id = ?
                    """)
                .params(me, why, spec.id())
                .update();
        }
    }

    /** Retires the effective version (the specification is no longer used for new studies). Reason required. */
    @RequirePermission("spec.manage")
    @Transactional
    public SpecVersionDetails retire(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        SpecificationSummary spec = visibleSpec(current.specificationId());
        access.requireAt("spec.manage", spec.siteId());
        String next = SpecLifecycle.next(current.status(), SpecLifecycle.Action.RETIRE);
        audit.open("spec_version.retire");
        setStatus(spec, current, next, "RETIRE", reason);
        return version(versionId);
    }

    // ------------------------------------------------------------------ helpers

    /** This version becomes EFFECTIVE today; the one effective before it is SUPERSEDED. */
    private void makeEffective(SpecificationSummary spec, VersionRow current, @Nullable String reason) {
        jdbc.sql("SELECT " + VERSION_COLUMNS + """
                  FROM app.spec_version
                 WHERE specification_id = ? AND status = 'EFFECTIVE' AND deleted_at IS NULL
                   FOR UPDATE""")
            .param(spec.id()).query(VERSION).optional()
            .ifPresent(previous -> setStatus(spec, previous, "SUPERSEDED", "SUPERSEDE",
                    "Superseded by version " + current.versionNo()));
        byte[] hash = RecordHasher.contentHash(signedContent(spec, current));
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("APPROVE", VERSION_RECORD, current.id(),
                current.version() + 1, key(spec, current), spec.siteId(),
                List.of(new FieldChange("status", current.status(), "EFFECTIVE"),
                        new FieldChange("effectiveFrom", null, LocalDate.now(ZoneOffset.UTC))),
                current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.spec_version SET status = 'EFFECTIVE', effective_from = current_date, content_hash = ?,
                       version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(hash, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
    }

    private VersionRow setStatus(SpecificationSummary spec, VersionRow current, String next, String action,
                                 @Nullable String reason) {
        audit.record(MasterDataSupport.actor(), new AuditEventDraft(action, VERSION_RECORD, current.id(),
                current.version() + 1, key(spec, current), spec.siteId(),
                List.of(new FieldChange("status", current.status(), next)), current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.spec_version SET status = ?, version = version + 1, updated_at = clock_timestamp(),
                       updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(next, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return versionRow(current.id(), true);
    }

    /**
     * Writes the draft's tests: updates the row of each test (restoring it if it was removed earlier, because a
     * test appears at most once per version), inserts new ones and logically deletes the ones left out.
     */
    private void writeTests(UUID versionId, List<SpecTest> tests, UUID me) {
        Map<UUID, UUID> rowByMethod = new HashMap<>();
        jdbc.sql("SELECT id, test_method_id FROM app.spec_test WHERE spec_version_id = ?")
                .param(versionId)
                .query(rs -> {
                    rowByMethod.put(rs.getObject("test_method_id", UUID.class), rs.getObject("id", UUID.class));
                });
        Set<UUID> kept = new HashSet<>();
        for (SpecTest t : tests) {
            kept.add(t.testMethodId());
            UUID rowId = rowByMethod.get(t.testMethodId());
            if (rowId == null) {
                jdbc.sql("""
                        INSERT INTO app.spec_test (tenant_id, id, created_by, spec_version_id, test_method_id,
                            sort_order, display_name, results_format, unit_code, replicates, decimals, lower_limit,
                            upper_limit, text_limit, alert_lower, alert_upper, trendable)
                        VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                    // as a list: optional limits are stored as NULL (JdbcClient's varargs are declared non-null)
                    .params(Arrays.asList(UUID.randomUUID(), me, versionId, t.testMethodId(), t.sortOrder(),
                            t.displayName(), t.resultsFormat(), t.unitCode(), t.replicates(), t.decimals(),
                            t.lowerLimit(), t.upperLimit(), t.textLimit(), t.alertLower(), t.alertUpper(),
                            t.trendable()))
                    .update();
            } else {
                jdbc.sql("""
                        UPDATE app.spec_test SET sort_order = ?, display_name = ?, unit_code = ?, replicates = ?,
                            decimals = ?, lower_limit = ?, upper_limit = ?, text_limit = ?, alert_lower = ?,
                            alert_upper = ?, trendable = ?, deleted_at = NULL, deleted_by = NULL,
                            delete_reason = NULL, version = version + 1, updated_at = clock_timestamp(),
                            updated_by = ?
                         WHERE id = ?
                        """)
                    // as a list: optional limits are stored as NULL (JdbcClient's varargs are declared non-null)
                    .params(Arrays.asList(t.sortOrder(), t.displayName(), t.unitCode(), t.replicates(), t.decimals(),
                            t.lowerLimit(), t.upperLimit(), t.textLimit(), t.alertLower(), t.alertUpper(),
                            t.trendable(), me, rowId))
                    .update();
            }
        }
        rowByMethod.forEach((methodId, rowId) -> {
            if (!kept.contains(methodId)) {
                jdbc.sql("""
                        UPDATE app.spec_test SET deleted_at = clock_timestamp(), deleted_by = ?,
                               delete_reason = 'Removed from the draft', version = version + 1
                         WHERE id = ? AND deleted_at IS NULL
                        """)
                    .params(me, rowId)
                    .update();
            }
        });
    }

    private SpecVersionDetails details(VersionRow v, SpecificationSummary spec) {
        UUID previous = jdbc.sql("""
                SELECT id FROM app.spec_version
                 WHERE specification_id = ? AND version_no < ? AND deleted_at IS NULL
                 ORDER BY version_no DESC LIMIT 1
                """)
            .params(v.specificationId(), v.versionNo()).query(UUID.class).optional().orElse(null);
        OffsetDateTime updated = v.updatedAt();
        return new SpecVersionDetails(v.id(), v.version(), spec, v.versionNo(), v.status(), v.effectiveFrom(),
                v.changeSummary(), tests(v.id()), previous, signatures.signaturesOf(VERSION_RECORD, v.id()),
                approvals.progress(VERSION_RECORD, APPROVAL_KIND, v.id(), spec.siteId()), v.createdAt().toInstant(),
                updated == null ? null : updated.toInstant());
    }

    private List<SpecTest> tests(UUID versionId) {
        return jdbc.sql("""
                SELECT t.test_method_id, m.code AS test_code, m.status AS test_status, t.sort_order, t.display_name,
                       t.results_format, t.unit_code, t.replicates, t.decimals, t.lower_limit, t.upper_limit,
                       t.text_limit, t.alert_lower, t.alert_upper, t.trendable
                  FROM app.spec_test t
                  JOIN app.test_method m ON m.tenant_id = t.tenant_id AND m.id = t.test_method_id
                 WHERE t.spec_version_id = ? AND t.deleted_at IS NULL
                 ORDER BY t.sort_order, m.code
                """)
            .param(versionId).query(TEST).list();
    }

    /** Library tests referenced by a draft, for validation and defaults. */
    private Map<UUID, SpecRules.LibraryTest> library(List<SpecTestInput> tests) {
        String[] ids = tests.stream().map(SpecTestInput::testMethodId).filter(Objects::nonNull)
                .map(UUID::toString).distinct().toArray(String[]::new);
        Map<UUID, SpecRules.LibraryTest> out = new HashMap<>();
        if (ids.length == 0) {
            return out;
        }
        jdbc.sql("""
                SELECT id, code, name, status, results_format, unit_code, replicates, decimals, trendable
                  FROM app.test_method WHERE id = ANY (CAST(? AS uuid[])) AND deleted_at IS NULL
                """)
            .param(ids)
            .query(rs -> {
                UUID id = rs.getObject("id", UUID.class);
                out.put(id, new SpecRules.LibraryTest(id, rs.getString("code"), rs.getString("name"),
                        rs.getString("status"), rs.getString("results_format"), rs.getString("unit_code"),
                        rs.getInt("replicates"), rs.getObject("decimals", Integer.class), rs.getBoolean("trendable")));
            });
        return out;
    }

    /** Tests of the previous version: they may stay in this draft even if retired in the library since. */
    private Set<UUID> carriedOver(VersionRow v) {
        return new HashSet<>(jdbc.sql("""
                SELECT t.test_method_id FROM app.spec_test t
                  JOIN app.spec_version p ON p.tenant_id = t.tenant_id AND p.id = t.spec_version_id
                 WHERE p.specification_id = ? AND p.version_no < ? AND p.deleted_at IS NULL
                   AND t.deleted_at IS NULL
                """)
            .params(v.specificationId(), v.versionNo())
            .query((rs, _) -> Objects.requireNonNull(rs.getObject(1, UUID.class))).list());
    }

    /** What is compared for the audit trail of a draft. */
    private static Map<String, @Nullable Object> content(@Nullable String changeSummary, List<SpecTest> tests) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("changeSummary", changeSummary);
        m.putAll(SpecTest.content(tests));
        return m;
    }

    /** What a signature is bound to: the specification header, the version number and every test with limits. */
    private Map<String, @Nullable Object> signedContent(SpecificationSummary spec, VersionRow v) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("specificationId", spec.id());
        m.put("specNumber", spec.specNumber());
        m.put("title", spec.title());
        m.put("productId", spec.productId());
        m.put("versionNo", (long) v.versionNo());
        m.putAll(content(v.changeSummary(), tests(v.id())));
        return m;
    }

    private @Nullable UUID lastContentEditor(UUID versionId) {
        return jdbc.sql("""
                SELECT actor_user_id FROM audit.audit_event
                 WHERE record_type = ? AND record_id = ? AND action IN ('CREATE', 'UPDATE')
                 ORDER BY seq DESC LIMIT 1
                """)
            .params(VERSION_RECORD, versionId).query(UUID.class).optional().orElse(null);
    }

    private VersionRow lockedVersion(UUID versionId, long expectedVersion) {
        VersionRow v = versionRow(versionId, true);
        if (v.version() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        return v;
    }

    private VersionRow versionRow(UUID versionId, boolean forUpdate) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + " FROM app.spec_version WHERE id = ? AND deleted_at IS NULL"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param(versionId).query(VERSION).optional()
                .orElseThrow(() -> ApiException.notFound("Specification version"));
    }

    /** The specification if it exists and its product's site is readable; otherwise 404 (no existence leak). */
    private SpecificationSummary visibleSpec(UUID specificationId) {
        SpecificationSummary s = jdbc.sql(SUMMARY_SELECT + " AND s.id = ?")
                .param(specificationId).query(SUMMARY).optional()
                .orElseThrow(() -> ApiException.notFound("Specification"));
        if (!access.hasAt("masterdata.view", s.siteId())) {
            throw ApiException.notFound("Specification");
        }
        return s;
    }

    /** The product's site and status, if the user can see it; otherwise 404. */
    private record ProductRef(UUID id, UUID siteId, String status) { }

    private ProductRef product(UUID productId) {
        return jdbc.sql("SELECT id, site_id, status FROM app.product WHERE id = ? AND deleted_at IS NULL")
                .param(productId)
                .query((rs, _) -> new ProductRef(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
                        rs.getString("status")))
                .optional()
                .filter(p -> access.hasAt("masterdata.view", p.siteId()))
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static String key(SpecificationSummary spec, VersionRow v) {
        return spec.specNumber() + " v" + v.versionNo();
    }
}
