package com.arrheniq.study;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.audit.FieldDiff;
import com.arrheniq.kernel.esign.ApprovalLevel;
import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.RecordHasher;
import com.arrheniq.kernel.esign.SignatureService;
import com.arrheniq.kernel.security.Access;
import com.arrheniq.kernel.security.CurrentUser;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.ReauthEvidence;
import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.kernel.security.SiteScope;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Study login and workflow (URS-STU-001/002/004/005). Site-scoped through the product: read {@code study.view},
 * log in / edit / submit / discard {@code study.create}, approve / reject {@code study.approve}. A study refers to
 * an EFFECTIVE specification version and an APPROVED protocol version of its product; approving it publishes
 * {@link StudyApproved}, and scheduling generates the schedule in the same transaction.
 */
@Service
public class StudyService {

    static final String STUDY_RECORD = "STUDY";
    static final String GROUP_RECORD = "STUDY_GROUP";
    /** Tenant setting {@code approval.study}: NONE, SINGLE or DOUBLE. */
    static final String APPROVAL_KIND = "study";

    static final int MAX_CONDITIONS = 10;
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;

    private static final String SUMMARY_SELECT = """
            SELECT s.id, s.study_code, s.study_group_id, s.site_id, s.product_id, p.code AS product_code,
                   p.name AS product_name, s.lot_number, s.condition_code,
                   COALESCE(cond.label, s.condition_code) AS condition_label,
                   pr.id AS protocol_id, pr.protocol_number, s.protocol_version_id, pv.version_no AS protocol_version_no,
                   pv.study_type, s.spec_version_id, sp.spec_number, sv.version_no AS spec_version_no,
                   s.on_stability_date, s.status,
                   (SELECT min(t.scheduled_date) FROM app.study_timepoint t
                     WHERE t.tenant_id = s.tenant_id AND t.study_id = s.id AND t.status = 'SCHEDULED'
                       AND t.deleted_at IS NULL) AS next_pull_date
              FROM app.study s
              JOIN app.product p ON p.tenant_id = s.tenant_id AND p.id = s.product_id
              JOIN app.protocol_version pv ON pv.tenant_id = s.tenant_id AND pv.id = s.protocol_version_id
              JOIN app.protocol pr ON pr.tenant_id = pv.tenant_id AND pr.id = pv.protocol_id
              JOIN app.spec_version sv ON sv.tenant_id = s.tenant_id AND sv.id = s.spec_version_id
              JOIN app.specification sp ON sp.tenant_id = sv.tenant_id AND sp.id = sv.specification_id
              LEFT JOIN LATERAL (SELECT dv.label FROM app.dictionary_value dv
                                   JOIN app.dictionary d ON d.tenant_id = dv.tenant_id AND d.id = dv.dictionary_id
                                  WHERE d.code = 'STORAGE_CONDITION' AND dv.code = s.condition_code
                                    AND (dv.site_id IS NULL OR dv.site_id = s.site_id) AND dv.deleted_at IS NULL
                                  ORDER BY dv.site_id NULLS LAST LIMIT 1) cond ON true
             WHERE s.deleted_at IS NULL""";

    private static final RowMapper<StudySummary> SUMMARY = (rs, _) -> new StudySummary(
            rs.getObject("id", UUID.class), rs.getString("study_code"), rs.getObject("study_group_id", UUID.class),
            rs.getObject("site_id", UUID.class), rs.getObject("product_id", UUID.class),
            rs.getString("product_code"), rs.getString("product_name"), rs.getString("lot_number"),
            rs.getString("condition_code"), rs.getString("condition_label"), rs.getObject("protocol_id", UUID.class),
            rs.getString("protocol_number"), rs.getObject("protocol_version_id", UUID.class),
            rs.getInt("protocol_version_no"), rs.getString("study_type"), rs.getObject("spec_version_id", UUID.class),
            rs.getString("spec_number"), rs.getInt("spec_version_no"),
            rs.getObject("on_stability_date", LocalDate.class), rs.getString("status"),
            rs.getObject("next_pull_date", LocalDate.class));

    /** The editable fields and workflow columns of one study row. */
    private record Row(UUID id, long version, String status, UUID siteId, UUID productId, String studyCode,
                       String lotNumber, @Nullable LocalDate manufactureDate, @Nullable LocalDate expiryDate,
                       LocalDate onStabilityDate, String conditionCode, String reasonCode, String studyLengthCode,
                       String storageLocationCode, @Nullable String proprietaryStudyId, @Nullable String notes,
                       UUID specVersionId, UUID protocolVersionId, int unitsPlaced, int reserveUnits,
                       @Nullable String fillUnitCode, @Nullable String scheduleAlgorithm,
                       @Nullable OffsetDateTime approvedAt, @Nullable UUID updatedBy, OffsetDateTime createdAt,
                       @Nullable OffsetDateTime updatedAt) {

        /** What the audit trail compares (field by field) and what a signature is bound to. */
        Map<String, @Nullable Object> auditMap() {
            Map<String, @Nullable Object> m = new LinkedHashMap<>();
            m.put("lotNumber", lotNumber);
            m.put("manufactureDate", manufactureDate == null ? null : manufactureDate.toString());
            m.put("expiryDate", expiryDate == null ? null : expiryDate.toString());
            m.put("onStabilityDate", onStabilityDate.toString());
            m.put("conditionCode", conditionCode);
            m.put("reasonCode", reasonCode);
            m.put("studyLengthCode", studyLengthCode);
            m.put("storageLocationCode", storageLocationCode);
            m.put("proprietaryStudyId", proprietaryStudyId);
            m.put("notes", notes);
            m.put("specVersionId", specVersionId.toString());
            m.put("protocolVersionId", protocolVersionId.toString());
            m.put("unitsPlaced", (long) unitsPlaced);
            m.put("reserveUnits", (long) reserveUnits);
            m.put("fillUnitCode", fillUnitCode);
            return m;
        }
    }

    private static final String ROW_COLUMNS = """
            id, version, status, site_id, product_id, study_code, lot_number, manufacture_date, expiry_date,
            on_stability_date, condition_code, reason_code, study_length_code, storage_location_code,
            proprietary_study_id, study_text, spec_version_id, protocol_version_id, units_placed, reserve_units,
            fill_unit_code, schedule_algorithm, approved_at, updated_by, created_at, updated_at""";

    private static final RowMapper<Row> ROW = (rs, _) -> new Row(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getString("status"),
            rs.getObject("site_id", UUID.class), rs.getObject("product_id", UUID.class), rs.getString("study_code"),
            rs.getString("lot_number"), rs.getObject("manufacture_date", LocalDate.class),
            rs.getObject("expiry_date", LocalDate.class), rs.getObject("on_stability_date", LocalDate.class),
            rs.getString("condition_code"), rs.getString("reason_code"), rs.getString("study_length_code"),
            rs.getString("storage_location_code"), rs.getString("proprietary_study_id"), rs.getString("study_text"),
            rs.getObject("spec_version_id", UUID.class), rs.getObject("protocol_version_id", UUID.class),
            rs.getInt("units_placed"), rs.getInt("reserve_units"), rs.getString("fill_unit_code"),
            rs.getString("schedule_algorithm"), rs.getObject("approved_at", OffsetDateTime.class),
            rs.getObject("updated_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));

    /** An approved protocol version as a study needs it. */
    private record ProtocolRef(UUID versionId, String protocolNumber, int versionNo, String status, UUID productId,
                               @Nullable String conditionCode, int unitsPlanned, int reserveUnits,
                               @Nullable String fillUnitCode, int timepoints) { }

    private record ProductRef(UUID id, UUID siteId, String code, String status) { }

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final SignatureService signatures;
    private final ApprovalService approvals;
    private final Access access;
    private final ApplicationEventPublisher events;

    public StudyService(JdbcClient jdbc, AuditService audit, SignatureService signatures, ApprovalService approvals,
                        Access access, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.signatures = signatures;
        this.approvals = approvals;
        this.access = access;
        this.events = events;
    }

    // ------------------------------------------------------------------ reading

    @RequirePermission("study.view")
    @Transactional(readOnly = true)
    public List<StudySummary> list(@Nullable UUID productId, @Nullable String status, @Nullable String q, int limit,
                                   int offset) {
        SiteScope scope = CurrentUserHolder.require().grants().scopeOf("study.view");
        StringBuilder sql = new StringBuilder(SUMMARY_SELECT);
        List<Object> params = new ArrayList<>();
        if (!scope.allSites()) {
            sql.append(" AND s.site_id = ANY (CAST(? AS uuid[]))");
            params.add(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (productId != null) {
            sql.append(" AND s.product_id = ?");
            params.add(productId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (s.study_code ILIKE ? ESCAPE '\\' OR s.lot_number ILIKE ? ESCAPE '\\'"
                    + " OR p.code ILIKE ? ESCAPE '\\' OR p.name ILIKE ? ESCAPE '\\')");
            String like = likePattern(q);
            params.addAll(List.of(like, like, like, like));
        }
        sql.append(" ORDER BY s.study_code DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, Math.min(limit, 200)));
        params.add(Math.max(0, offset));
        return jdbc.sql(sql.toString()).params(params).query(SUMMARY).list();
    }

    @RequirePermission("study.view")
    @Transactional(readOnly = true)
    public StudyDetails get(UUID studyId) {
        Row row = row(studyId, false);
        StudySummary summary = visibleStudy(studyId);
        return details(row, summary);
    }

    // ------------------------------------------------------------------ login

    /**
     * Logs in a lot under one or more conditions: one study per condition, linked by a study group, each with its
     * own study ID. The studies start as DRAFT.
     */
    @RequirePermission("study.create")
    @Transactional
    public List<StudySummary> login(StudyLoginInput in) {
        StudyValidation v = new StudyValidation();
        v.required("productId", in.productId());
        v.required("specVersionId", in.specVersionId());
        v.throwIfAny();
        ProductRef product = product(Objects.requireNonNull(in.productId()));
        access.requireAt("study.create", product.siteId());
        if (!product.status().equals("APPROVED")) {
            v.add("productId", "NOT_APPROVED", "the product must be approved before a study can be logged in");
        }
        checkSpecVersion(v, product, Objects.requireNonNull(in.specVersionId()));

        String lot = StudyValidation.trimToNull(in.lotNumber());
        v.required("lotNumber", lot);
        v.maxLength("lotNumber", lot, 40);
        checkDates(v, in.manufactureDate(), in.expiryDate(), in.onStabilityDate());
        String reason = code(in.reasonCode());
        String length = code(in.studyLengthCode());
        checkListValue(v, "reasonCode", "STUDY_REASON", reason, product.siteId());
        checkListValue(v, "studyLengthCode", "STUDY_LENGTH", length, product.siteId());
        String proprietary = StudyValidation.trimToNull(in.proprietaryStudyId());
        v.maxLength("proprietaryStudyId", proprietary, 40);
        String notes = StudyValidation.trimToNull(in.notes());
        v.maxLength("notes", notes, 2000);

        List<StudyLoginInput.ConditionInput> conditions = in.conditions() == null ? List.of() : in.conditions();
        if (conditions.isEmpty()) {
            v.add("conditions", "REQUIRED", "choose at least one condition (an approved protocol)");
        } else if (conditions.size() > MAX_CONDITIONS) {
            v.add("conditions", "OUT_OF_RANGE", "at most " + MAX_CONDITIONS);
        }
        List<ProtocolRef> protocols = new ArrayList<>();
        Set<String> seenConditions = new HashSet<>();
        Set<UUID> seenProtocols = new HashSet<>();
        for (int i = 0; i < conditions.size() && i < MAX_CONDITIONS; i++) {
            StudyLoginInput.ConditionInput c = conditions.get(i);
            String f = "conditions[" + i + "].";
            String location = code(c.storageLocationCode());
            checkListValue(v, f + "storageLocationCode", "STORAGE_LOCATION", location, product.siteId());
            if (c.protocolVersionId() == null) {
                v.add(f + "protocolVersionId", "REQUIRED", null);
                continue;
            }
            ProtocolRef p = protocol(c.protocolVersionId());
            if (p == null || !p.productId().equals(product.id())) {
                v.add(f + "protocolVersionId", "OTHER_PRODUCT", "not a protocol of this product");
                continue;
            }
            checkProtocol(v, f + "protocolVersionId", p);
            if (!seenProtocols.add(p.versionId())) {
                v.add(f + "protocolVersionId", "DUPLICATE", "this protocol is already chosen");
            } else if (p.conditionCode() != null && !seenConditions.add(p.conditionCode())) {
                v.add(f + "protocolVersionId", "DUPLICATE", "one study per condition: " + p.conditionCode()
                        + " is already chosen");
            }
            protocols.add(p);
        }
        v.throwIfAny();

        CurrentUser me = CurrentUserHolder.require();
        audit.open("study.login");
        UUID groupId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app.study_group (tenant_id, id, created_by, product_id, lot_number)
                VALUES (app.current_tenant(), ?, ?, ?, ?)
                """)
            .params(groupId, me.userId(), product.id(), Objects.requireNonNull(lot))
            .update();
        Map<String, @Nullable Object> group = new LinkedHashMap<>();
        group.put("productId", product.id().toString());
        group.put("lotNumber", lot);
        group.put("studies", (long) protocols.size());
        audit.record(actor(), new AuditEventDraft("CREATE", GROUP_RECORD, groupId, 0L, product.code() + " " + lot,
                product.siteId(), FieldDiff.between(null, group), null, null, null));

        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < protocols.size(); i++) {
            ProtocolRef p = protocols.get(i);
            String location = Objects.requireNonNull(code(conditions.get(i).storageLocationCode()));
            UUID studyId = UUID.randomUUID();
            String studyCode = nextStudyCode(product.siteId());
            jdbc.sql("""
                    INSERT INTO app.study (tenant_id, id, created_by, site_id, study_group_id, study_code,
                                           proprietary_study_id, product_id, spec_version_id, protocol_version_id,
                                           lot_number, manufacture_date, expiry_date, on_stability_date,
                                           condition_code, reason_code, study_length_code, storage_location_code,
                                           fill_unit_code, units_placed, reserve_units, study_text)
                    VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
                .params(Arrays.asList(studyId, me.userId(), product.siteId(), groupId, studyCode, proprietary,
                        product.id(), in.specVersionId(), p.versionId(), lot, in.manufactureDate(),
                        in.expiryDate(), in.onStabilityDate(), p.conditionCode(), reason, length, location,
                        p.fillUnitCode(), p.unitsPlanned(), p.reserveUnits(), notes))
                .update();
            Row row = row(studyId, false);
            List<FieldChange> changes = new ArrayList<>(FieldDiff.between(null, row.auditMap()));
            changes.add(new FieldChange("studyCode", null, studyCode));
            changes.add(new FieldChange("studyGroupId", null, groupId.toString()));
            audit.record(actor(), new AuditEventDraft("CREATE", STUDY_RECORD, studyId, 0L, studyCode,
                    product.siteId(), changes, null, null, null));
            created.add(studyId);
        }
        return jdbc.sql(SUMMARY_SELECT + " AND s.study_group_id = ? ORDER BY s.study_code")
                .param(groupId).query(SUMMARY).list().stream()
                .filter(s -> created.contains(s.id())).toList();
    }

    /** Edits a DRAFT study. */
    @RequirePermission("study.create")
    @Transactional
    public StudyDetails update(UUID studyId, long expectedVersion, StudyUpdateInput in) {
        Row current = locked(studyId, expectedVersion);
        StudySummary summary = visibleStudy(studyId);
        access.requireAt("study.create", current.siteId());
        StudyLifecycle.next(current.status(), StudyLifecycle.Action.EDIT);

        StudyValidation v = new StudyValidation();
        String lot = StudyValidation.trimToNull(in.lotNumber());
        v.required("lotNumber", lot);
        v.maxLength("lotNumber", lot, 40);
        checkDates(v, in.manufactureDate(), in.expiryDate(), in.onStabilityDate());
        String reason = code(in.reasonCode());
        String length = code(in.studyLengthCode());
        String location = code(in.storageLocationCode());
        checkListValue(v, "reasonCode", "STUDY_REASON", reason, current.siteId());
        checkListValue(v, "studyLengthCode", "STUDY_LENGTH", length, current.siteId());
        checkListValue(v, "storageLocationCode", "STORAGE_LOCATION", location, current.siteId());
        String proprietary = StudyValidation.trimToNull(in.proprietaryStudyId());
        v.maxLength("proprietaryStudyId", proprietary, 40);
        String notes = StudyValidation.trimToNull(in.notes());
        v.maxLength("notes", notes, 2000);
        v.throwIfAny();

        Row after = new Row(current.id(), current.version(), current.status(), current.siteId(), current.productId(),
                current.studyCode(), Objects.requireNonNull(lot), in.manufactureDate(), in.expiryDate(),
                Objects.requireNonNull(in.onStabilityDate()), current.conditionCode(), Objects.requireNonNull(reason),
                Objects.requireNonNull(length), Objects.requireNonNull(location), proprietary, notes,
                current.specVersionId(), current.protocolVersionId(), current.unitsPlaced(), current.reserveUnits(),
                current.fillUnitCode(), current.scheduleAlgorithm(), current.approvedAt(), current.updatedBy(),
                current.createdAt(), current.updatedAt());
        List<FieldChange> changes = FieldDiff.between(current.auditMap(), after.auditMap());
        if (changes.isEmpty()) {
            return details(current, summary);
        }
        audit.open("study.update");
        audit.record(actor(), new AuditEventDraft("UPDATE", STUDY_RECORD, studyId, current.version() + 1,
                current.studyCode(), current.siteId(), changes, current.status(), null, null));
        int n = jdbc.sql("""
                UPDATE app.study SET lot_number = ?, manufacture_date = ?, expiry_date = ?, on_stability_date = ?,
                       reason_code = ?, study_length_code = ?, storage_location_code = ?, proprietary_study_id = ?,
                       study_text = ?, version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(lot, in.manufactureDate(), in.expiryDate(), in.onStabilityDate(), reason, length,
                    location, proprietary, notes, CurrentUserHolder.require().userId(), studyId, expectedVersion))
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return get(studyId);
    }

    // ------------------------------------------------------------------ workflow

    /**
     * Submits a DRAFT for approval. The specification and protocol versions must still be the current ones. With
     * {@code approval.study = NONE} the study is approved at once and its schedule generated.
     */
    @RequirePermission("study.create")
    @Transactional
    public StudyDetails submit(UUID studyId, long expectedVersion) {
        Row current = locked(studyId, expectedVersion);
        visibleStudy(studyId);
        access.requireAt("study.create", current.siteId());
        String next = StudyLifecycle.next(current.status(), StudyLifecycle.Action.SUBMIT);
        checkStillCurrent(current);
        audit.open("study.submit");
        Row submitted = setStatus(current, next, "SUBMIT", null);
        if (approvals.levelFor(APPROVAL_KIND, current.siteId()) == ApprovalLevel.NONE) {
            makeApproved(submitted, "No approval required (tenant setting approval.study = NONE)");
        }
        return get(studyId);
    }

    /**
     * Approves with an e-signature bound to the study's content (Part 11 §11.70). When complete, the study is
     * APPROVED and its schedule generated in the same transaction. The author, last editor and submitter can't sign.
     */
    @RequirePermission("study.approve")
    @Transactional
    public StudyDetails approve(UUID studyId, long expectedVersion, @Nullable String comment,
                                Supplier<ReauthEvidence> reauthentication) {
        Row current = locked(studyId, expectedVersion);
        visibleStudy(studyId);
        access.requireAt("study.approve", current.siteId());
        StudyLifecycle.next(current.status(), StudyLifecycle.Action.APPROVE);
        checkStillCurrent(current);
        Set<UUID> excluded = new HashSet<>();
        if (current.updatedBy() != null) {
            excluded.add(current.updatedBy());
        }
        UUID editor = lastContentEditor(studyId);
        if (editor != null) {
            excluded.add(editor);
        }
        audit.open("study.approve");
        ApprovalService.Step step = approvals.approve(new ApprovalService.Approvable(STUDY_RECORD, APPROVAL_KIND,
                studyId, current.version(), current.studyCode(), current.siteId(), signedContent(current), excluded),
                comment, reauthentication);
        if (step.complete()) {
            makeApproved(current, null);
        }
        return get(studyId);
    }

    @RequirePermission("study.approve")
    @Transactional
    public StudyDetails reject(UUID studyId, long expectedVersion, @Nullable String reason) {
        Row current = locked(studyId, expectedVersion);
        visibleStudy(studyId);
        access.requireAt("study.approve", current.siteId());
        String next = StudyLifecycle.next(current.status(), StudyLifecycle.Action.REJECT);
        audit.open("study.reject");
        Row rejected = setStatus(current, next, "REJECT", reason);
        int superseded = signatures.supersedeAll(STUDY_RECORD, studyId, "Rejected: " + Objects.requireNonNull(reason));
        if (superseded > 0) {
            audit.record(actor(), new AuditEventDraft("SIGNATURE_SUPERSEDE", STUDY_RECORD, studyId,
                    rejected.version(), current.studyCode(), current.siteId(),
                    List.of(new FieldChange("supersededSignatures", null, (long) superseded)), next, reason, null));
        }
        return get(studyId);
    }

    /** Discards a DRAFT study (logically deleted, with a reason). Its study ID is not reused. */
    @RequirePermission("study.create")
    @Transactional
    public void discard(UUID studyId, long expectedVersion, @Nullable String reason) {
        Row current = locked(studyId, expectedVersion);
        visibleStudy(studyId);
        access.requireAt("study.create", current.siteId());
        StudyLifecycle.next(current.status(), StudyLifecycle.Action.DISCARD);
        audit.open("study.discard");
        audit.record(actor(), new AuditEventDraft("LOGICAL_DELETE", STUDY_RECORD, studyId, current.version() + 1,
                current.studyCode(), current.siteId(), List.of(new FieldChange("deleted", false, true)),
                current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.study SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """)
            .params(CurrentUserHolder.require().userId(), reason == null ? "" : reason, studyId, expectedVersion)
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
    }

    // ------------------------------------------------------------------ checks

    private void checkSpecVersion(StudyValidation v, ProductRef product, UUID specVersionId) {
        record Spec(UUID productId, String status) { }
        Spec s = jdbc.sql("""
                SELECT sp.product_id, sv.status FROM app.spec_version sv
                  JOIN app.specification sp ON sp.tenant_id = sv.tenant_id AND sp.id = sv.specification_id
                 WHERE sv.id = ? AND sv.deleted_at IS NULL AND sp.deleted_at IS NULL
                """)
            .param(specVersionId)
            .query((rs, _) -> new Spec(rs.getObject(1, UUID.class), rs.getString(2)))
            .optional().orElse(null);
        if (s == null || !s.productId().equals(product.id())) {
            v.add("specVersionId", "OTHER_PRODUCT", "not a specification of this product");
        } else if (!s.status().equals("EFFECTIVE")) {
            v.add("specVersionId", "NOT_EFFECTIVE", "use the approved (effective) specification version");
        }
    }

    private static void checkProtocol(StudyValidation v, String field, ProtocolRef p) {
        if (!p.status().equals("APPROVED")) {
            v.add(field, "NOT_APPROVED", p.protocolNumber() + " v" + p.versionNo() + " is not approved");
        } else if (p.conditionCode() == null) {
            v.add(field, "NO_CONDITION", p.protocolNumber() + " has no storage condition");
        } else if (p.timepoints() == 0) {
            v.add(field, "NO_TIMEPOINTS", p.protocolNumber() + " has no time points");
        }
    }

    private static void checkDates(StudyValidation v, @Nullable LocalDate manufacture, @Nullable LocalDate expiry,
                                   @Nullable LocalDate onStability) {
        v.required("onStabilityDate", onStability);
        if (onStability != null && (onStability.getYear() < MIN_YEAR || onStability.getYear() > MAX_YEAR)) {
            v.add("onStabilityDate", "OUT_OF_RANGE", MIN_YEAR + "–" + MAX_YEAR);
        }
        if (manufacture != null && onStability != null && manufacture.isAfter(onStability)) {
            v.add("manufactureDate", "ORDER", "the lot can't be made after it is placed on stability");
        }
        if (manufacture != null && expiry != null && !expiry.isAfter(manufacture)) {
            v.add("expiryDate", "ORDER", "must be after the manufacture date");
        }
    }

    /** The value must be an ACTIVE entry of the list, tenant-wide or for this site. */
    private void checkListValue(StudyValidation v, String field, String dictionary, @Nullable String code,
                                UUID siteId) {
        if (code == null) {
            v.add(field, "REQUIRED", null);
            return;
        }
        boolean ok = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM app.dictionary_value dv
                                 JOIN app.dictionary d ON d.tenant_id = dv.tenant_id AND d.id = dv.dictionary_id
                                WHERE d.code = ? AND dv.code = ? AND dv.status = 'ACTIVE' AND dv.deleted_at IS NULL
                                  AND (dv.site_id IS NULL OR dv.site_id = ?))
                """)
            .params(dictionary, code, siteId).query(Boolean.class).single();
        if (!ok) {
            v.add(field, "UNKNOWN", "not an active value of the list " + dictionary);
        }
    }

    /** The specification and protocol versions must still be in force when the study is submitted or approved. */
    private void checkStillCurrent(Row study) {
        StudyValidation v = new StudyValidation();
        String spec = jdbc.sql("SELECT status FROM app.spec_version WHERE id = ? AND deleted_at IS NULL")
                .param(study.specVersionId()).query(String.class).optional().orElse("DELETED");
        if (!spec.equals("EFFECTIVE")) {
            v.add("specVersionId", "NOT_EFFECTIVE", "the specification version is now " + spec.toLowerCase(Locale.ROOT)
                    + "; discard this study and log it in again");
        }
        ProtocolRef p = protocol(study.protocolVersionId());
        if (p == null || !p.status().equals("APPROVED")) {
            v.add("protocolVersionId", "NOT_APPROVED", "the protocol version is no longer approved"
                    + "; discard this study and log it in again");
        }
        v.throwIfAny();
    }

    // ------------------------------------------------------------------ helpers

    private void makeApproved(Row current, @Nullable String reason) {
        byte[] hash = RecordHasher.contentHash(signedContent(current));
        audit.record(actor(), new AuditEventDraft("APPROVE", STUDY_RECORD, current.id(), current.version() + 1,
                current.studyCode(), current.siteId(), List.of(new FieldChange("status", current.status(),
                        "APPROVED")), current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.study SET status = 'APPROVED', approved_at = clock_timestamp(), content_hash = ?,
                       version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(hash, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        // scheduling generates the schedule here, in this transaction (URS-STU-005); a failure rolls back
        events.publishEvent(new StudyApproved(current.id(), current.studyCode(), current.siteId(),
                current.protocolVersionId(), current.onStabilityDate()));
    }

    private Row setStatus(Row current, String next, String action, @Nullable String reason) {
        audit.record(actor(), new AuditEventDraft(action, STUDY_RECORD, current.id(), current.version() + 1,
                current.studyCode(), current.siteId(), List.of(new FieldChange("status", current.status(), next)),
                current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.study SET status = ?, version = version + 1, updated_at = clock_timestamp(), updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(next, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return row(current.id(), true);
    }

    /** What a signature is bound to: the study's identity and complete content. */
    private static Map<String, @Nullable Object> signedContent(Row s) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("studyId", s.id().toString());
        m.put("studyCode", s.studyCode());
        m.put("productId", s.productId().toString());
        m.putAll(s.auditMap());
        return m;
    }

    /**
     * Next study ID (URS-STU-002). The per-tenant counter row is locked by the upsert, so concurrent logins wait
     * for each other and never get the same number; an ID already taken (e.g. migrated) is skipped.
     */
    private String nextStudyCode(UUID siteId) {
        String pattern = jdbc.sql("""
                SELECT value #>> '{}' FROM app.tenant_setting
                 WHERE setting_key = 'study.code_pattern' AND deleted_at IS NULL AND (site_id IS NULL OR site_id = ?)
                 ORDER BY site_id NULLS LAST LIMIT 1
                """)
            .param(siteId).query(String.class).optional()
            .orElseThrow(() -> new IllegalStateException("tenant setting study.code_pattern is missing"));
        StudyCodes.validate(pattern);
        String zone = jdbc.sql("SELECT time_zone FROM app.site WHERE id = ?").param(siteId).query(String.class).single();
        int year = LocalDate.now(ZoneId.of(zone)).getYear();
        String scope = StudyCodes.scopeKey(pattern, year);
        for (int attempt = 0; attempt < 1000; attempt++) {
            long number = jdbc.sql("""
                    INSERT INTO app.tenant_sequence (tenant_id, name, scope_key, next_value)
                    VALUES (app.current_tenant(), 'STUDY_CODE', ?, 2)
                    ON CONFLICT (tenant_id, name, scope_key)
                    DO UPDATE SET next_value = app.tenant_sequence.next_value + 1
                    RETURNING next_value - 1
                    """)
                .param(scope).query(Long.class).single();
            String candidate = StudyCodes.format(pattern, year, number);
            boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.study WHERE study_code = ?)")
                    .param(candidate).query(Boolean.class).single();
            if (!taken) {
                return candidate;
            }
        }
        throw new IllegalStateException("no free study ID after 1000 attempts; check study.code_pattern");
    }

    private StudyDetails details(Row r, StudySummary summary) {
        List<StudyDetails.Sibling> siblings = summary.studyGroupId() == null ? List.of() : jdbc.sql(
                        SUMMARY_SELECT + " AND s.study_group_id = ? AND s.id <> ? ORDER BY s.study_code")
                .params(summary.studyGroupId(), r.id()).query(SUMMARY).list().stream()
                .map(s -> new StudyDetails.Sibling(s.id(), s.studyCode(), s.conditionCode(), s.conditionLabel(),
                        s.status()))
                .toList();
        OffsetDateTime updated = r.updatedAt();
        OffsetDateTime approved = r.approvedAt();
        return new StudyDetails(summary, r.version(), r.manufactureDate(), r.expiryDate(), r.reasonCode(),
                listLabel("STUDY_REASON", r.reasonCode(), r.siteId()), r.studyLengthCode(),
                listLabel("STUDY_LENGTH", r.studyLengthCode(), r.siteId()), r.storageLocationCode(),
                listLabel("STORAGE_LOCATION", r.storageLocationCode(), r.siteId()), r.proprietaryStudyId(),
                r.notes(), r.unitsPlaced(), r.reserveUnits(), r.fillUnitCode(), r.scheduleAlgorithm(),
                approved == null ? null : approved.toInstant(), siblings,
                signatures.signaturesOf(STUDY_RECORD, r.id()),
                approvals.progress(STUDY_RECORD, APPROVAL_KIND, r.id(), r.siteId()), r.createdAt().toInstant(),
                updated == null ? null : updated.toInstant());
    }

    private String listLabel(String dictionary, String code, UUID siteId) {
        return jdbc.sql("""
                SELECT dv.label FROM app.dictionary_value dv
                  JOIN app.dictionary d ON d.tenant_id = dv.tenant_id AND d.id = dv.dictionary_id
                 WHERE d.code = ? AND dv.code = ? AND (dv.site_id IS NULL OR dv.site_id = ?) AND dv.deleted_at IS NULL
                 ORDER BY dv.site_id NULLS LAST LIMIT 1
                """)
            .params(dictionary, code, siteId).query(String.class).optional().orElse(code);
    }

    private @Nullable ProtocolRef protocol(UUID versionId) {
        return jdbc.sql("""
                SELECT pv.id, pr.protocol_number, pv.version_no, pv.status, pr.product_id, pv.condition_code,
                       COALESCE((SELECT sum(t.units_planned) FROM app.protocol_timepoint t
                                  WHERE t.tenant_id = pv.tenant_id AND t.protocol_version_id = pv.id
                                    AND t.deleted_at IS NULL), 0) AS units_planned,
                       pv.reserve_units, pv.fill_unit_code,
                       (SELECT count(*) FROM app.protocol_timepoint t
                         WHERE t.tenant_id = pv.tenant_id AND t.protocol_version_id = pv.id
                           AND t.deleted_at IS NULL) AS timepoints
                  FROM app.protocol_version pv
                  JOIN app.protocol pr ON pr.tenant_id = pv.tenant_id AND pr.id = pv.protocol_id
                 WHERE pv.id = ? AND pv.deleted_at IS NULL AND pr.deleted_at IS NULL
                """)
            .param(versionId)
            .query((rs, _) -> new ProtocolRef(rs.getObject("id", UUID.class), rs.getString("protocol_number"),
                    rs.getInt("version_no"), rs.getString("status"), rs.getObject("product_id", UUID.class),
                    rs.getString("condition_code"), rs.getInt("units_planned"), rs.getInt("reserve_units"),
                    rs.getString("fill_unit_code"), rs.getInt("timepoints")))
            .optional().orElse(null);
    }

    private ProductRef product(UUID productId) {
        ProductRef p = jdbc.sql("SELECT id, site_id, code, status FROM app.product WHERE id = ? AND deleted_at IS NULL")
                .param(productId)
                .query((rs, _) -> new ProductRef(rs.getObject("id", UUID.class), rs.getObject("site_id", UUID.class),
                        rs.getString("code"), rs.getString("status")))
                .optional().orElseThrow(() -> ApiException.notFound("Product"));
        if (!access.hasAt("masterdata.view", p.siteId())) {
            throw ApiException.notFound("Product");
        }
        return p;
    }

    private @Nullable UUID lastContentEditor(UUID studyId) {
        return jdbc.sql("""
                SELECT actor_user_id FROM audit.audit_event
                 WHERE record_type = ? AND record_id = ? AND action IN ('CREATE', 'UPDATE')
                 ORDER BY seq DESC LIMIT 1
                """)
            .params(STUDY_RECORD, studyId).query(UUID.class).optional().orElse(null);
    }

    private Row locked(UUID studyId, long expectedVersion) {
        Row r = row(studyId, true);
        if (r.version() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        return r;
    }

    private Row row(UUID studyId, boolean forUpdate) {
        return jdbc.sql("SELECT " + ROW_COLUMNS + " FROM app.study WHERE id = ? AND deleted_at IS NULL"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param(studyId).query(ROW).optional()
                .orElseThrow(() -> ApiException.notFound("Study"));
    }

    private StudySummary visibleStudy(UUID studyId) {
        StudySummary s = jdbc.sql(SUMMARY_SELECT + " AND s.id = ?")
                .param(studyId).query(SUMMARY).optional()
                .orElseThrow(() -> ApiException.notFound("Study"));
        if (!access.hasAt("study.view", s.siteId())) {
            throw ApiException.notFound("Study");
        }
        return s;
    }

    private static @Nullable String code(@Nullable String value) {
        String t = StudyValidation.trimToNull(value);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    private static String likePattern(String q) {
        return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static AuditActor actor() {
        CurrentUser u = CurrentUserHolder.require();
        return new AuditActor(u.userId(), u.displayName(), AuditActor.Type.USER);
    }
}
