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

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
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
 * Protocols (URS-PRO-001/002/003/005/007): time points with planned units, the test × time-point grid, study type
 * and intended storage condition, versioned and approved by e-signature. Site-scoped through the product: read
 * {@code masterdata.view}, edit {@code protocol.manage}, approve/reject {@code protocol.approve}. Only a DRAFT
 * is edited; an APPROVED version never changes ({@link ProtocolLifecycle}).
 */
@Service
public class ProtocolService {

    static final String PROTOCOL_RECORD = "PROTOCOL";
    static final String VERSION_RECORD = "PROTOCOL_VERSION";
    /** Tenant setting {@code approval.protocol}: NONE, SINGLE or DOUBLE. */
    static final String APPROVAL_KIND = "protocol";

    private static final String SUMMARY_SELECT = """
            SELECT r.id, r.protocol_number, r.name, r.product_id, p.code AS product_code, p.name AS product_name,
                   p.site_id, av.version_no AS approved_no, av.effective_from AS approved_from,
                   COALESCE(ov.study_type, av.study_type) AS study_type,
                   COALESCE(ov.condition_code, av.condition_code) AS condition_code,
                   ov.id AS open_id, ov.version_no AS open_no, ov.status AS open_status
              FROM app.protocol r
              JOIN app.product p ON p.tenant_id = r.tenant_id AND p.id = r.product_id
              LEFT JOIN app.protocol_version av ON av.tenant_id = r.tenant_id AND av.protocol_id = r.id
                                               AND av.status = 'APPROVED' AND av.deleted_at IS NULL
              LEFT JOIN LATERAL (SELECT x.id, x.version_no, x.status, x.study_type, x.condition_code
                                   FROM app.protocol_version x
                                  WHERE x.tenant_id = r.tenant_id AND x.protocol_id = r.id
                                    AND x.status IN ('DRAFT', 'IN_REVIEW') AND x.deleted_at IS NULL
                                  ORDER BY x.version_no DESC LIMIT 1) ov ON true
             WHERE r.deleted_at IS NULL""";

    private static final RowMapper<ProtocolSummary> SUMMARY = (rs, _) -> new ProtocolSummary(
            rs.getObject("id", UUID.class), rs.getString("protocol_number"), rs.getString("name"),
            rs.getObject("product_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"),
            rs.getObject("site_id", UUID.class), rs.getObject("approved_no", Integer.class),
            rs.getObject("approved_from", LocalDate.class), rs.getString("study_type"),
            rs.getString("condition_code"), rs.getObject("open_id", UUID.class),
            rs.getObject("open_no", Integer.class), rs.getString("open_status"));

    private record VersionRow(UUID id, long version, UUID protocolId, int versionNo, String status,
                              String studyType, @Nullable String conditionCode, int reserveUnits,
                              @Nullable String fillUnitCode, @Nullable String changeSummary,
                              @Nullable LocalDate effectiveFrom, @Nullable UUID updatedBy, OffsetDateTime createdAt,
                              @Nullable OffsetDateTime updatedAt) { }

    private static final String VERSION_COLUMNS = """
            id, version, protocol_id, version_no, status, study_type, condition_code, reserve_units, fill_unit_code,
            change_summary, effective_from, updated_by, created_at, updated_at""";

    private static final RowMapper<VersionRow> VERSION = (rs, _) -> new VersionRow(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getObject("protocol_id", UUID.class),
            rs.getInt("version_no"), rs.getString("status"), rs.getString("study_type"),
            rs.getString("condition_code"), rs.getInt("reserve_units"), rs.getString("fill_unit_code"),
            rs.getString("change_summary"), rs.getObject("effective_from", LocalDate.class),
            rs.getObject("updated_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    private record ProductRef(UUID id, UUID siteId, String status) { }

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final SignatureService signatures;
    private final ApprovalService approvals;
    private final Access access;

    public ProtocolService(JdbcClient jdbc, AuditService audit, SignatureService signatures, ApprovalService approvals,
                           Access access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.signatures = signatures;
        this.approvals = approvals;
        this.access = access;
    }

    // ------------------------------------------------------------------ reading

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<ProtocolSummary> list(@Nullable UUID productId, @Nullable String q, int limit, int offset) {
        SiteScope scope = CurrentUserHolder.require().grants().scopeOf("masterdata.view");
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT);
        List<Object> params = new ArrayList<>();
        if (!scope.allSites()) {
            sql.append(" AND p.site_id = ANY (CAST(? AS uuid[]))");
            params.add(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (productId != null) {
            sql.append(" AND r.product_id = ?");
            params.add(productId);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (r.protocol_number ILIKE ? ESCAPE '\\' OR r.name ILIKE ? ESCAPE '\\'"
                    + " OR p.code ILIKE ? ESCAPE '\\')");
            String like = MasterDataSupport.likePattern(q);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY r.protocol_number LIMIT ? OFFSET ?");
        params.add(MasterDataSupport.limit(limit));
        params.add(Math.max(0, offset));
        return jdbc.sql(sql.toString()).params(params).query(SUMMARY).list();
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public ProtocolDetails get(UUID protocolId) {
        ProtocolSummary protocol = visibleProtocol(protocolId);
        List<ProtocolVersionSummary> versions = jdbc.sql("""
                SELECT v.id, v.version_no, v.status, v.study_type, v.condition_code, v.effective_from,
                       v.change_summary, v.created_at, v.updated_at,
                       (SELECT count(*) FROM app.protocol_timepoint t WHERE t.tenant_id = v.tenant_id
                           AND t.protocol_version_id = v.id AND t.deleted_at IS NULL) AS timepoint_count,
                       (SELECT count(*) FROM app.protocol_test t WHERE t.tenant_id = v.tenant_id
                           AND t.protocol_version_id = v.id AND t.deleted_at IS NULL) AS test_count
                  FROM app.protocol_version v
                 WHERE v.protocol_id = ? AND v.deleted_at IS NULL
                 ORDER BY v.version_no DESC
                """)
            .param(protocolId)
            .query((rs, _) -> new ProtocolVersionSummary(rs.getObject("id", UUID.class), rs.getInt("version_no"),
                    rs.getString("status"), rs.getString("study_type"), rs.getString("condition_code"),
                    rs.getObject("effective_from", LocalDate.class), rs.getString("change_summary"),
                    rs.getInt("timepoint_count"), rs.getInt("test_count"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    MasterDataSupport.instant(rs.getObject("updated_at", OffsetDateTime.class))))
            .list();
        return new ProtocolDetails(protocol, versions);
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public ProtocolVersionDetails version(UUID versionId) {
        VersionRow v = versionRow(versionId, false);
        return details(v, visibleProtocol(v.protocolId()));
    }

    // ------------------------------------------------------------------ creating and editing

    /**
     * Creates the protocol and its version 1 as a DRAFT, optionally pre-filled from an ICH template (study type,
     * condition, time points) and from a specification version of the same product (its tests, at every time
     * point). Everything can be changed in the draft.
     */
    @RequirePermission("protocol.manage")
    @Transactional
    public ProtocolDetails create(@Nullable UUID productId, @Nullable String protocolNumber, @Nullable String name,
                                  @Nullable String templateCode, @Nullable UUID specVersionId) {
        Validation v = new Validation();
        String number = Validation.normalizeCode(protocolNumber);
        v.code("protocolNumber", number);
        v.required("name", name, 200);
        if (productId == null) {
            v.add("productId", "REQUIRED", null);
        }
        v.throwIfAny();
        ProductRef product = product(Objects.requireNonNull(productId));
        access.requireAt("protocol.manage", product.siteId());
        if ("RETIRED".equals(product.status())) {
            throw ApiException.invalidState(product.status(), "ADD_PROTOCOL");
        }
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.protocol WHERE protocol_number = ?)")
                .param(number).query(Boolean.class).single();
        if (taken) {
            throw ApiException.duplicate("protocolNumber", number);
        }
        ProtocolContent initial = initialContent(product, templateCode, specVersionId);

        String cleanName = Objects.requireNonNull(Validation.trimToNull(name));
        UUID protocolId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID me = CurrentUserHolder.require().userId();
        audit.open("protocol.create");
        jdbc.sql("""
                INSERT INTO app.protocol (tenant_id, id, created_by, product_id, protocol_number, name)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?)
                """)
            .params(protocolId, me, product.id(), number, cleanName)
            .update();
        jdbc.sql("""
                INSERT INTO app.protocol_version (tenant_id, id, created_by, protocol_id, version_no, status,
                                                  study_type, condition_code)
                VALUES (app.current_tenant(), ?, ?, ?, 1, 'DRAFT', ?, ?)
                """)
            // as a list: the condition may be NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(versionId, me, protocolId, initial.studyType(), initial.conditionCode()))
            .update();
        writeContent(versionId, initial, me);
        Map<String, @Nullable Object> header = new LinkedHashMap<>();
        header.put("productId", product.id());
        header.put("protocolNumber", number);
        header.put("name", cleanName);
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", PROTOCOL_RECORD, protocolId, 0L, number,
                product.siteId(), FieldDiff.between(null, header), null, null, null));
        List<FieldChange> created = new ArrayList<>(FieldDiff.between(null, initial.auditMap()));
        created.add(new FieldChange("versionNo", null, 1L));
        if (templateCode != null) {
            created.add(new FieldChange("fromTemplate", null, templateCode));
        }
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", VERSION_RECORD, versionId, 0L,
                number + " v1", product.siteId(), created, null, null, null));
        return get(protocolId);
    }

    /** Saves a DRAFT: the complete content (time points, grid, units, study type, condition). */
    @RequirePermission("protocol.manage")
    @Transactional
    public ProtocolVersionDetails saveDraft(UUID versionId, long expectedVersion, ProtocolInput input) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.manage", protocol.siteId());
        ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.EDIT);

        List<UUID> ids = input.tests() == null ? List.of() : input.tests().stream()
                .map(ProtocolInput.TestInput::testMethodId).filter(Objects::nonNull).toList();
        ProtocolContent after = ProtocolRules.validate(input, library(ids), carriedOver(current), activeConditions());
        ProtocolContent before = content(current);
        List<FieldChange> changes = FieldDiff.between(before.auditMap(), after.auditMap());
        if (changes.isEmpty()) {
            return details(current, protocol);
        }
        UUID me = CurrentUserHolder.require().userId();
        audit.open("protocol_version.update");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", VERSION_RECORD, versionId,
                current.version() + 1, key(protocol, current), protocol.siteId(), changes, current.status(), null,
                null));
        writeContent(versionId, after, me);
        int n = jdbc.sql("""
                UPDATE app.protocol_version SET study_type = ?, condition_code = ?, reserve_units = ?,
                       fill_unit_code = ?, change_summary = ?, version = version + 1,
                       updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(after.studyType(), after.conditionCode(), after.reserveUnits(), after.fillUnitCode(),
                    after.changeSummary(), me, versionId, expectedVersion))
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return version(versionId);
    }

    /** Starts the next version as a DRAFT copy of the approved one (or the latest). One open version at a time. */
    @RequirePermission("protocol.manage")
    @Transactional
    public ProtocolVersionDetails newVersion(UUID protocolId) {
        // lock first, then read: serialises concurrent "new version" clicks
        jdbc.sql("SELECT id FROM app.protocol WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(protocolId).query(UUID.class).optional();
        ProtocolSummary protocol = visibleProtocol(protocolId);
        access.requireAt("protocol.manage", protocol.siteId());
        if (protocol.openVersionId() != null) {
            throw new ApiException(409, "ARQ-MD-020", "Version " + protocol.openVersionNo() + " is still "
                    + Objects.requireNonNull(protocol.openStatus()).toLowerCase(Locale.ROOT).replace('_', ' ')
                    + "; finish or discard it first", List.of());
        }
        VersionRow base = jdbc.sql("SELECT " + VERSION_COLUMNS + """
                  FROM app.protocol_version
                 WHERE protocol_id = ? AND deleted_at IS NULL
                 ORDER BY (status = 'APPROVED') DESC, version_no DESC
                 LIMIT 1""")
            .param(protocolId).query(VERSION).single();
        ProtocolContent copied = content(base);
        int next = jdbc.sql("SELECT max(version_no) + 1 FROM app.protocol_version WHERE protocol_id = ?")
                .param(protocolId).query(Integer.class).single();
        UUID versionId = UUID.randomUUID();
        UUID me = CurrentUserHolder.require().userId();
        audit.open("protocol_version.create");
        jdbc.sql("""
                INSERT INTO app.protocol_version (tenant_id, id, created_by, protocol_id, version_no, status,
                                                  study_type, condition_code, reserve_units, fill_unit_code)
                VALUES (app.current_tenant(), ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
                """)
            // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(versionId, me, protocolId, next, copied.studyType(), copied.conditionCode(),
                    copied.reserveUnits(), copied.fillUnitCode()))
            .update();
        writeContent(versionId, copied, me);
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", VERSION_RECORD, versionId, 0L,
                protocol.protocolNumber() + " v" + next, protocol.siteId(),
                List.of(new FieldChange("versionNo", null, (long) next),
                        new FieldChange("copiedFrom", null, "v" + base.versionNo()),
                        new FieldChange("status", null, "DRAFT")), null, null, null));
        return version(versionId);
    }

    // ------------------------------------------------------------------ workflow

    /** Submits a DRAFT; with {@code approval.protocol = NONE} it is approved at once (no signature, audited). */
    @RequirePermission("protocol.manage")
    @Transactional
    public ProtocolVersionDetails submit(UUID versionId, long expectedVersion) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.manage", protocol.siteId());
        String next = ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.SUBMIT);
        ProtocolContent c = content(current);
        Validation v = new Validation();
        if (c.timepoints().isEmpty()) {
            v.add("timepoints", "REQUIRED", "add at least one time point before submitting");
        }
        if (c.tests().isEmpty()) {
            v.add("tests", "REQUIRED", "add at least one test before submitting");
        }
        v.throwIfAny();
        audit.open("protocol_version.submit");
        VersionRow submitted = setStatus(protocol, current, next, "SUBMIT", null);
        if (approvals.levelFor(APPROVAL_KIND, protocol.siteId()) == ApprovalLevel.NONE) {
            makeApproved(protocol, submitted, "No approval required (tenant setting approval.protocol = NONE)");
        }
        return version(versionId);
    }

    /**
     * Approves with an e-signature bound to this version's content (Part 11 §11.70); DOUBLE approval: the first
     * signature means REVIEWED. When complete, this version is APPROVED (usable for study login) and the
     * previously approved one SUPERSEDED. Author and submitter can't sign.
     */
    @RequirePermission("protocol.approve")
    @Transactional
    public ProtocolVersionDetails approve(UUID versionId, long expectedVersion, @Nullable String comment,
                                          Supplier<ReauthEvidence> reauthentication) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.approve", protocol.siteId());
        ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.APPROVE);
        Set<UUID> excluded = new HashSet<>();
        UUID submitter = current.updatedBy();
        if (submitter != null) {
            excluded.add(submitter);
        }
        UUID editor = lastContentEditor(versionId);
        if (editor != null) {
            excluded.add(editor);
        }
        audit.open("protocol_version.approve");
        ApprovalService.Step step = approvals.approve(new ApprovalService.Approvable(VERSION_RECORD, APPROVAL_KIND,
                versionId, current.version(), key(protocol, current), protocol.siteId(),
                signedContent(protocol, current), excluded), comment, reauthentication);
        if (step.complete()) {
            makeApproved(protocol, current, null);
        }
        return version(versionId);
    }

    @RequirePermission("protocol.approve")
    @Transactional
    public ProtocolVersionDetails reject(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.approve", protocol.siteId());
        String next = ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.REJECT);
        audit.open("protocol_version.reject");
        VersionRow rejected = setStatus(protocol, current, next, "REJECT", reason);
        int superseded = signatures.supersedeAll(VERSION_RECORD, versionId,
                "Rejected: " + Objects.requireNonNull(reason));
        if (superseded > 0) {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("SIGNATURE_SUPERSEDE", VERSION_RECORD,
                    versionId, rejected.version(), key(protocol, current), protocol.siteId(),
                    List.of(new FieldChange("supersededSignatures", null, (long) superseded)), next, reason, null));
        }
        return version(versionId);
    }

    /** Discards a DRAFT (logically deleted, with a reason); discarding version 1 discards the protocol. */
    @RequirePermission("protocol.manage")
    @Transactional
    public void discard(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.manage", protocol.siteId());
        ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.DISCARD);
        UUID me = CurrentUserHolder.require().userId();
        audit.open("protocol_version.discard");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("LOGICAL_DELETE", VERSION_RECORD, versionId,
                current.version() + 1, key(protocol, current), protocol.siteId(),
                List.of(new FieldChange("deleted", false, true)), current.status(), reason, null));
        String why = reason == null ? "" : reason;
        jdbc.sql("""
                UPDATE app.protocol_version SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """)
            .params(me, why, versionId, expectedVersion)
            .update();
        deleteContent(versionId, me, why);
        boolean anyLeft = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM app.protocol_version WHERE protocol_id = ? AND deleted_at IS NULL)
                """)
            .param(protocol.id()).query(Boolean.class).single();
        if (!anyLeft) {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("LOGICAL_DELETE", PROTOCOL_RECORD,
                    protocol.id(), null, protocol.protocolNumber(), protocol.siteId(),
                    List.of(new FieldChange("deleted", false, true)), null, reason, null));
            jdbc.sql("""
                    UPDATE app.protocol SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                           version = version + 1
                     WHERE id = ?
                    """)
                .params(me, why, protocol.id())
                .update();
        }
    }

    /** Retires the approved version: it can no longer be used for new studies. Reason required. */
    @RequirePermission("protocol.manage")
    @Transactional
    public ProtocolVersionDetails retire(UUID versionId, long expectedVersion, @Nullable String reason) {
        VersionRow current = lockedVersion(versionId, expectedVersion);
        ProtocolSummary protocol = visibleProtocol(current.protocolId());
        access.requireAt("protocol.manage", protocol.siteId());
        String next = ProtocolLifecycle.next(current.status(), ProtocolLifecycle.Action.RETIRE);
        audit.open("protocol_version.retire");
        setStatus(protocol, current, next, "RETIRE", reason);
        return version(versionId);
    }

    // ------------------------------------------------------------------ content

    /** Version 1: from an ICH template and/or the tests of a specification version of the same product. */
    private ProtocolContent initialContent(ProductRef product, @Nullable String templateCode,
                                           @Nullable UUID specVersionId) {
        String studyType = "LONG_TERM";
        String condition = null;
        List<ProtocolContent.TimePoint> timepoints = new ArrayList<>();
        if (templateCode != null && !templateCode.isBlank()) {
            record Template(String studyType, String conditionCode, String unit, List<BigDecimal> timepoints) { }
            Template t = jdbc.sql("""
                    SELECT study_type, condition_code, timepoint_unit, timepoints
                      FROM platform.ref_protocol_template WHERE code = ?
                    """)
                .param(templateCode.trim().toUpperCase(Locale.ROOT))
                .query((rs, _) -> new Template(rs.getString("study_type"), rs.getString("condition_code"),
                        rs.getString("timepoint_unit"), numbers(rs.getArray("timepoints"))))
                .optional()
                .orElseThrow(() -> invalid("templateCode", "UNKNOWN", "no such ICH template"));
            studyType = ProtocolRules.STUDY_TYPES.contains(t.studyType()) ? t.studyType() : "OTHER";
            condition = activeConditions().contains(t.conditionCode()) ? t.conditionCode() : null;
            for (BigDecimal value : t.timepoints()) {
                timepoints.add(new ProtocolContent.TimePoint(value, t.unit(),
                        ProtocolRules.defaultLabel(value, t.unit()), 0));
            }
        }
        List<ProtocolContent.Test> tests = new ArrayList<>();
        if (specVersionId != null) {
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < timepoints.size(); i++) {
                all.add(i);
            }
            record SpecTestRef(UUID id, String code, String name, String status, String format) { }
            UUID specProduct = jdbc.sql("""
                    SELECT s.product_id FROM app.spec_version v
                      JOIN app.specification s ON s.tenant_id = v.tenant_id AND s.id = v.specification_id
                     WHERE v.id = ? AND v.deleted_at IS NULL AND s.deleted_at IS NULL
                    """)
                .param(specVersionId).query(UUID.class).optional()
                .orElseThrow(() -> invalid("specVersionId", "UNKNOWN", "no such specification version"));
            if (!specProduct.equals(product.id())) {
                throw invalid("specVersionId", "OTHER_PRODUCT", "the specification belongs to another product");
            }
            jdbc.sql("""
                    SELECT m.id, m.code, m.name, m.status, m.results_format
                      FROM app.spec_test t
                      JOIN app.test_method m ON m.tenant_id = t.tenant_id AND m.id = t.test_method_id
                     WHERE t.spec_version_id = ? AND t.deleted_at IS NULL AND m.status = 'ACTIVE'
                     ORDER BY t.sort_order
                    """)
                .param(specVersionId)
                .query((rs, _) -> new SpecTestRef(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getString("status"), rs.getString("results_format")))
                .list()
                .forEach(r -> tests.add(new ProtocolContent.Test(r.id(), r.code(), r.name(), r.status(), r.format(),
                        List.copyOf(all))));
        }
        return new ProtocolContent(studyType, condition, 0, null, null, timepoints, tests);
    }

    /** Replaces the draft's time points, tests and grid: logically deletes the old rows, inserts the new ones. */
    private void writeContent(UUID versionId, ProtocolContent c, UUID me) {
        deleteContent(versionId, me, "Replaced by a draft save");
        List<UUID> tpIds = new ArrayList<>();
        for (int i = 0; i < c.timepoints().size(); i++) {
            ProtocolContent.TimePoint t = c.timepoints().get(i);
            UUID id = UUID.randomUUID();
            tpIds.add(id);
            jdbc.sql("""
                    INSERT INTO app.protocol_timepoint (tenant_id, id, created_by, protocol_version_id, seq, value,
                                                        unit, label, units_planned)
                    VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                .params(id, me, versionId, i, t.value(), t.unit(), t.label(), t.unitsPlanned())
                .update();
        }
        for (int i = 0; i < c.tests().size(); i++) {
            ProtocolContent.Test t = c.tests().get(i);
            UUID testRow = UUID.randomUUID();
            jdbc.sql("""
                    INSERT INTO app.protocol_test (tenant_id, id, created_by, protocol_version_id, test_method_id,
                                                   sort_order)
                    VALUES (app.current_tenant(), ?, ?, ?, ?, ?)
                    """)
                .params(testRow, me, versionId, t.testMethodId(), (i + 1) * 10)
                .update();
            for (int idx : t.at()) {
                jdbc.sql("""
                        INSERT INTO app.protocol_matrix_cell (tenant_id, id, created_by, protocol_test_id,
                                                              protocol_timepoint_id, required)
                        VALUES (app.current_tenant(), ?, ?, ?, ?, true)
                        """)
                    .params(UUID.randomUUID(), me, testRow, tpIds.get(idx))
                    .update();
            }
        }
    }

    private void deleteContent(UUID versionId, UUID me, String why) {
        jdbc.sql("""
                UPDATE app.protocol_matrix_cell c SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?
                 WHERE c.deleted_at IS NULL AND c.protocol_test_id IN
                       (SELECT t.id FROM app.protocol_test t WHERE t.protocol_version_id = ?)
                """)
            .params(me, why, versionId).update();
        jdbc.sql("""
                UPDATE app.protocol_test SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?
                 WHERE protocol_version_id = ? AND deleted_at IS NULL
                """)
            .params(me, why, versionId).update();
        jdbc.sql("""
                UPDATE app.protocol_timepoint SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?
                 WHERE protocol_version_id = ? AND deleted_at IS NULL
                """)
            .params(me, why, versionId).update();
    }

    private ProtocolContent content(VersionRow v) {
        record TpRow(UUID id, ProtocolContent.TimePoint tp) { }
        List<TpRow> tps = jdbc.sql("""
                SELECT id, value, unit, label, units_planned FROM app.protocol_timepoint
                 WHERE protocol_version_id = ? AND deleted_at IS NULL ORDER BY seq
                """)
            .param(v.id())
            .query((rs, _) -> {
                BigDecimal value = rs.getBigDecimal("value");
                String unit = rs.getString("unit");
                String label = rs.getString("label");
                return new TpRow(rs.getObject("id", UUID.class), new ProtocolContent.TimePoint(value, unit,
                        label == null ? ProtocolRules.defaultLabel(value, unit) : label, rs.getInt("units_planned")));
            })
            .list();
        Map<UUID, Integer> tpIndex = new HashMap<>();
        for (int i = 0; i < tps.size(); i++) {
            tpIndex.put(tps.get(i).id(), i);
        }
        Map<UUID, List<Integer>> cells = new HashMap<>();
        jdbc.sql("""
                SELECT c.protocol_test_id, c.protocol_timepoint_id FROM app.protocol_matrix_cell c
                  JOIN app.protocol_test t ON t.tenant_id = c.tenant_id AND t.id = c.protocol_test_id
                 WHERE t.protocol_version_id = ? AND c.deleted_at IS NULL AND t.deleted_at IS NULL AND c.required
                """)
            .param(v.id())
            .query(rs -> {
                Integer idx = tpIndex.get(rs.getObject("protocol_timepoint_id", UUID.class));
                if (idx != null) {
                    cells.computeIfAbsent(rs.getObject("protocol_test_id", UUID.class), _ -> new ArrayList<>())
                            .add(idx);
                }
            });
        List<ProtocolContent.Test> tests = jdbc.sql("""
                SELECT t.id, m.id AS method_id, m.code, m.name, m.status, m.results_format
                  FROM app.protocol_test t
                  JOIN app.test_method m ON m.tenant_id = t.tenant_id AND m.id = t.test_method_id
                 WHERE t.protocol_version_id = ? AND t.deleted_at IS NULL
                 ORDER BY t.sort_order, m.code
                """)
            .param(v.id())
            .query((rs, _) -> new ProtocolContent.Test(rs.getObject("method_id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getString("status"), rs.getString("results_format"),
                    cells.getOrDefault(rs.getObject("id", UUID.class), List.of()).stream().sorted().toList()))
            .list();
        return new ProtocolContent(v.studyType(), v.conditionCode(), v.reserveUnits(), v.fillUnitCode(),
                v.changeSummary(), tps.stream().map(TpRow::tp).toList(), tests);
    }

    /** Plan problems shown to the user; they don't block saving (URS-PRO-003: insufficient inventory). */
    static List<String> warnings(ProtocolContent c) {
        List<String> out = new ArrayList<>();
        int[] testsAt = new int[c.timepoints().size()];
        for (ProtocolContent.Test t : c.tests()) {
            for (int idx : t.at()) {
                testsAt[idx]++;
            }
        }
        for (int i = 0; i < c.timepoints().size(); i++) {
            ProtocolContent.TimePoint tp = c.timepoints().get(i);
            if (testsAt[i] > 0 && tp.unitsPlanned() == 0) {
                out.add("No units planned at " + tp.label() + ", although " + testsAt[i]
                        + (testsAt[i] == 1 ? " test runs" : " tests run") + " there");
            }
            if (testsAt[i] == 0) {
                out.add("No test runs at " + tp.label());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private void makeApproved(ProtocolSummary protocol, VersionRow current, @Nullable String reason) {
        jdbc.sql("SELECT " + VERSION_COLUMNS + """
                  FROM app.protocol_version
                 WHERE protocol_id = ? AND status = 'APPROVED' AND deleted_at IS NULL
                   FOR UPDATE""")
            .param(protocol.id()).query(VERSION).optional()
            .ifPresent(previous -> setStatus(protocol, previous, "SUPERSEDED", "SUPERSEDE",
                    "Superseded by version " + current.versionNo()));
        byte[] hash = RecordHasher.contentHash(signedContent(protocol, current));
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("APPROVE", VERSION_RECORD, current.id(),
                current.version() + 1, key(protocol, current), protocol.siteId(),
                List.of(new FieldChange("status", current.status(), "APPROVED"),
                        new FieldChange("effectiveFrom", null, LocalDate.now(ZoneOffset.UTC))),
                current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.protocol_version SET status = 'APPROVED', effective_from = current_date, content_hash = ?,
                       version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(hash, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
    }

    private VersionRow setStatus(ProtocolSummary protocol, VersionRow current, String next, String action,
                                 @Nullable String reason) {
        audit.record(MasterDataSupport.actor(), new AuditEventDraft(action, VERSION_RECORD, current.id(),
                current.version() + 1, key(protocol, current), protocol.siteId(),
                List.of(new FieldChange("status", current.status(), next)), current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.protocol_version SET status = ?, version = version + 1, updated_at = clock_timestamp(),
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

    private ProtocolVersionDetails details(VersionRow v, ProtocolSummary protocol) {
        UUID previous = jdbc.sql("""
                SELECT id FROM app.protocol_version
                 WHERE protocol_id = ? AND version_no < ? AND deleted_at IS NULL
                 ORDER BY version_no DESC LIMIT 1
                """)
            .params(v.protocolId(), v.versionNo()).query(UUID.class).optional().orElse(null);
        ProtocolContent c = content(v);
        String conditionLabel = c.conditionCode() == null ? null : jdbc.sql("""
                SELECT dv.label FROM app.dictionary_value dv
                  JOIN app.dictionary d ON d.tenant_id = dv.tenant_id AND d.id = dv.dictionary_id
                 WHERE d.code = 'STORAGE_CONDITION' AND dv.code = ? AND dv.site_id IS NULL AND dv.deleted_at IS NULL
                """)
            .param(c.conditionCode()).query(String.class).optional().orElse(c.conditionCode());
        OffsetDateTime updated = v.updatedAt();
        return new ProtocolVersionDetails(v.id(), v.version(), protocol, v.versionNo(), v.status(), v.effectiveFrom(),
                c, conditionLabel, c.totalUnits(), warnings(c), previous,
                signatures.signaturesOf(VERSION_RECORD, v.id()),
                approvals.progress(VERSION_RECORD, APPROVAL_KIND, v.id(), protocol.siteId()),
                v.createdAt().toInstant(), updated == null ? null : updated.toInstant());
    }

    /** What a signature is bound to: protocol header, version number and the complete content. */
    private Map<String, @Nullable Object> signedContent(ProtocolSummary protocol, VersionRow v) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("protocolId", protocol.id());
        m.put("protocolNumber", protocol.protocolNumber());
        m.put("name", protocol.name());
        m.put("productId", protocol.productId());
        m.put("versionNo", (long) v.versionNo());
        m.putAll(content(v).auditMap());
        return m;
    }

    private Map<UUID, SpecRules.LibraryTest> library(List<UUID> ids) {
        Map<UUID, SpecRules.LibraryTest> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.sql("""
                SELECT id, code, name, status, results_format, unit_code, replicates, decimals, trendable
                  FROM app.test_method WHERE id = ANY (CAST(? AS uuid[])) AND deleted_at IS NULL
                """)
            .param(ids.stream().map(UUID::toString).distinct().toArray(String[]::new))
            .query(rs -> {
                UUID id = rs.getObject("id", UUID.class);
                out.put(id, new SpecRules.LibraryTest(id, rs.getString("code"), rs.getString("name"),
                        rs.getString("status"), rs.getString("results_format"), rs.getString("unit_code"),
                        rs.getInt("replicates"), rs.getObject("decimals", Integer.class), rs.getBoolean("trendable")));
            });
        return out;
    }

    private Set<UUID> carriedOver(VersionRow v) {
        return new HashSet<>(jdbc.sql("""
                SELECT t.test_method_id FROM app.protocol_test t
                  JOIN app.protocol_version p ON p.tenant_id = t.tenant_id AND p.id = t.protocol_version_id
                 WHERE p.protocol_id = ? AND p.version_no < ? AND p.deleted_at IS NULL AND t.deleted_at IS NULL
                """)
            .params(v.protocolId(), v.versionNo())
            .query((rs, _) -> Objects.requireNonNull(rs.getObject(1, UUID.class))).list());
    }

    private Set<String> activeConditions() {
        return new HashSet<>(jdbc.sql("""
                SELECT dv.code FROM app.dictionary_value dv
                  JOIN app.dictionary d ON d.tenant_id = dv.tenant_id AND d.id = dv.dictionary_id
                 WHERE d.code = 'STORAGE_CONDITION' AND dv.status = 'ACTIVE' AND dv.deleted_at IS NULL
                """)
            .query((rs, _) -> Objects.requireNonNull(rs.getString(1))).list());
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
        return jdbc.sql("SELECT " + VERSION_COLUMNS + " FROM app.protocol_version WHERE id = ? AND deleted_at IS NULL"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param(versionId).query(VERSION).optional()
                .orElseThrow(() -> ApiException.notFound("Protocol version"));
    }

    private ProtocolSummary visibleProtocol(UUID protocolId) {
        ProtocolSummary p = jdbc.sql(SUMMARY_SELECT + " AND r.id = ?")
                .param(protocolId).query(SUMMARY).optional()
                .orElseThrow(() -> ApiException.notFound("Protocol"));
        if (!access.hasAt("masterdata.view", p.siteId())) {
            throw ApiException.notFound("Protocol");
        }
        return p;
    }

    private ProductRef product(UUID productId) {
        return jdbc.sql("SELECT id, site_id, status FROM app.product WHERE id = ? AND deleted_at IS NULL")
                .param(productId)
                .query((rs, _) -> new ProductRef(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
                        rs.getString("status")))
                .optional()
                .filter(p -> access.hasAt("masterdata.view", p.siteId()))
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static List<BigDecimal> numbers(Array array) throws SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(o -> (BigDecimal) o).toList();
    }

    private static ApiException invalid(String field, String code, String message) {
        return ApiException.invalid(List.of(new ApiException.FieldError(field, code, message)));
    }

    private static String key(ProtocolSummary protocol, VersionRow v) {
        return protocol.protocolNumber() + " v" + v.versionNo();
    }
}
