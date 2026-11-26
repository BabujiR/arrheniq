package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.audit.FieldDiff;
import com.arrheniq.kernel.esign.ApprovalLevel;
import com.arrheniq.kernel.esign.ApprovalService;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Products (URS-MD-002) with the approval workflow of {@link ProductLifecycle}. Site-scoped:
 * read {@code masterdata.view}, write {@code product.manage}, approve/reject {@code product.approve},
 * each checked at the product's site. Records outside the user's read scope are reported as not found.
 */
@Service
public class ProductService {

    static final String RECORD_TYPE = "PRODUCT";
    /** Tenant setting {@code approval.product}: NONE, SINGLE or DOUBLE. */
    static final String APPROVAL_KIND = "product";

    private static final String COLUMNS = """
            id, version, site_id, code, name, description, product_type, dosage_form, strength, expiration_status,
            expiration_value, expiration_unit, controlled_substance, status, created_by, updated_by, created_at,
            updated_at""";

    private static final RowMapper<Product> ROW = (rs, _) -> new Product(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getObject("site_id", UUID.class),
            rs.getString("code"), rs.getString("name"), rs.getString("description"), rs.getString("product_type"),
            rs.getString("dosage_form"), rs.getString("strength"), rs.getString("expiration_status"),
            rs.getObject("expiration_value", Integer.class), rs.getString("expiration_unit"),
            rs.getBoolean("controlled_substance"), rs.getString("status"), rs.getObject("created_by", UUID.class),
            rs.getObject("updated_by", UUID.class), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            MasterDataSupport.instant(rs.getObject("updated_at", OffsetDateTime.class)));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final SignatureService signatures;
    private final ApprovalService approvals;
    private final Access access;

    public ProductService(JdbcClient jdbc, AuditService audit, SignatureService signatures,
                          ApprovalService approvals, Access access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.signatures = signatures;
        this.approvals = approvals;
        this.access = access;
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<Product> list(@Nullable String status, @Nullable String q, @Nullable UUID siteId, int limit,
                              int offset) {
        SiteScope scope = CurrentUserHolder.require().grants().scopeOf("masterdata.view");
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM app.product WHERE deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        if (!scope.allSites()) {
            sql.append(" AND site_id = ANY (CAST(? AS uuid[]))");
            params.add(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (siteId != null) {
            sql.append(" AND site_id = ?");
            params.add(siteId);
        }
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
    public ProductDetails get(UUID id) {
        Product p = visible(id, false);
        return new ProductDetails(p, signatures.signaturesOf(RECORD_TYPE, id),
                approvals.progress(RECORD_TYPE, APPROVAL_KIND, id, p.siteId()));
    }

    @RequirePermission("product.manage")
    @Transactional
    public ProductDetails create(ProductInput input) {
        ProductInput.Clean c = ProductRules.validate(input);
        access.requireAt("product.manage", c.siteId());
        requireActiveSite(c.siteId());
        requireUniqueCode(c.code(), null);
        UUID id = UUID.randomUUID();
        audit.open("product.create");
        jdbc.sql("""
                INSERT INTO app.product (tenant_id, id, created_by, site_id, code, name, description, product_type,
                    dosage_form, strength, expiration_status, expiration_value, expiration_unit, controlled_substance)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(id, CurrentUserHolder.require().userId(), c.siteId(), c.code(), c.name(),
                    c.description(), c.productType(), c.dosageForm(), c.strength(), c.expirationStatus(),
                    c.expirationValue(), c.expirationUnit(), c.controlledSubstance()))
            .update();
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", RECORD_TYPE, id, 0L, c.code(),
                c.siteId(), FieldDiff.between(null, Product.auditMap(id, c, "DRAFT")), null, null, null));
        return get(id);
    }

    /**
     * Edits the product. In APPROVED status a reason is required (ReasonPolicy), every valid signature is
     * superseded and the product returns to DRAFT for re-approval (Part 11 §11.70).
     */
    @RequirePermission("product.manage")
    @Transactional
    public ProductDetails update(UUID id, long expectedVersion, ProductInput input, @Nullable String reason) {
        Product current = lockedCurrent(id, expectedVersion);
        access.requireAt("product.manage", current.siteId());
        String next = ProductLifecycle.next(current.status(), ProductLifecycle.Action.EDIT);
        ProductInput.Clean c = ProductRules.validate(input);
        if (!c.siteId().equals(current.siteId())) {
            access.requireAt("product.manage", c.siteId());
            requireActiveSite(c.siteId());
        }
        List<FieldChange> changes = new ArrayList<>(FieldDiff.between(
                Product.auditMap(id, current.clean(), current.status()), Product.auditMap(id, c, next)));
        if (changes.isEmpty() || (changes.size() == 1 && "status".equals(changes.getFirst().field()))) {
            return get(id);   // nothing in the content changed
        }
        requireUniqueCode(c.code(), id);
        audit.open("product.update");
        if ("APPROVED".equals(current.status())) {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", RECORD_TYPE, id,
                    current.version() + 1, c.code(), c.siteId(), changes, current.status(), reason, null));
            int superseded = signatures.supersedeAll(RECORD_TYPE, id, Objects.requireNonNull(reason));
            if (superseded > 0) {
                audit.record(MasterDataSupport.actor(), new AuditEventDraft("SIGNATURE_SUPERSEDE", RECORD_TYPE, id,
                        current.version() + 1, c.code(), c.siteId(),
                        List.of(new FieldChange("supersededSignatures", null, (long) superseded)), null,
                        reason, null));
            }
        } else {
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", RECORD_TYPE, id,
                    current.version() + 1, c.code(), c.siteId(), changes, current.status(), reason, null));
        }
        int n = jdbc.sql("""
                UPDATE app.product SET site_id = ?, code = ?, name = ?, description = ?, product_type = ?,
                    dosage_form = ?, strength = ?, expiration_status = ?, expiration_value = ?, expiration_unit = ?,
                    controlled_substance = ?, status = ?, version = version + 1, updated_at = clock_timestamp(),
                    updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            // as a list: optional fields are stored as NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(c.siteId(), c.code(), c.name(), c.description(), c.productType(), c.dosageForm(),
                    c.strength(), c.expirationStatus(), c.expirationValue(), c.expirationUnit(),
                    c.controlledSubstance(), next, CurrentUserHolder.require().userId(), id, expectedVersion))
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return get(id);
    }

    /**
     * Submits for approval. When the tenant configured no approval for products ({@code approval.product = NONE}),
     * the product becomes APPROVED at once, without a signature; the audit trail records why.
     */
    @RequirePermission("product.manage")
    @Transactional
    public ProductDetails submit(UUID id, long expectedVersion) {
        ProductDetails submitted = transition(id, expectedVersion, ProductLifecycle.Action.SUBMIT, "product.manage",
                null);
        Product p = submitted.product();
        if (approvals.levelFor(APPROVAL_KIND, p.siteId()) == ApprovalLevel.NONE) {
            Product current = lockedCurrent(id, p.version());
            setStatus(current, ProductLifecycle.next(current.status(), ProductLifecycle.Action.APPROVE),
                    ProductLifecycle.Action.APPROVE, "No approval required (tenant setting approval.product = NONE)");
            return get(id);
        }
        return submitted;
    }

    /** Rejects back to DRAFT; a first (REVIEWED) signature of a double approval no longer counts. */
    @RequirePermission("product.approve")
    @Transactional
    public ProductDetails reject(UUID id, long expectedVersion, @Nullable String reason) {
        ProductDetails rejected = transition(id, expectedVersion, ProductLifecycle.Action.REJECT, "product.approve",
                reason);
        int superseded = signatures.supersedeAll(RECORD_TYPE, id, "Rejected: " + Objects.requireNonNull(reason));
        if (superseded > 0) {
            Product p = rejected.product();
            audit.record(MasterDataSupport.actor(), new AuditEventDraft("SIGNATURE_SUPERSEDE", RECORD_TYPE, id,
                    p.version(), p.code(), p.siteId(),
                    List.of(new FieldChange("supersededSignatures", null, (long) superseded)), p.status(),
                    reason, null));
        }
        return rejected;
    }

    @RequirePermission("product.manage")
    @Transactional
    public ProductDetails retire(UUID id, long expectedVersion, @Nullable String reason) {
        return transition(id, expectedVersion, ProductLifecycle.Action.RETIRE, "product.manage", reason);
    }

    /**
     * Approves with an electronic signature bound to the reviewed content and version. With
     * {@code approval.product = DOUBLE} the first signature means REVIEWED and the product stays IN_REVIEW until a
     * different approver signs APPROVED. Neither signer may be the person who last changed the content or who
     * submitted it (segregation of duties). {@code reauthentication} supplies the single-use re-authentication
     * evidence for exactly this product version (401 if missing or stale, 409 if it was for another record).
     */
    @RequirePermission("product.approve")
    @Transactional
    public ProductDetails approve(UUID id, long expectedVersion, @Nullable String comment,
                                  Supplier<ReauthEvidence> reauthentication) {
        Product current = lockedCurrent(id, expectedVersion);
        access.requireAt("product.approve", current.siteId());
        String next = ProductLifecycle.next(current.status(), ProductLifecycle.Action.APPROVE);
        Set<UUID> excluded = new HashSet<>();
        UUID submitter = current.updatedBy();
        if (submitter != null) {
            excluded.add(submitter);
        }
        UUID editor = lastContentEditor(id);
        if (editor != null) {
            excluded.add(editor);
        }
        audit.open("product.approve");
        ApprovalService.Step step = approvals.approve(new ApprovalService.Approvable(RECORD_TYPE, APPROVAL_KIND, id,
                current.version(), current.code(), current.siteId(), current.businessContent(), excluded),
                comment, reauthentication);
        if (step.complete()) {
            setStatus(current, next, ProductLifecycle.Action.APPROVE, null);
        }
        return get(id);
    }

    private ProductDetails transition(UUID id, long expectedVersion, ProductLifecycle.Action action,
                                      String permission, @Nullable String reason) {
        Product current = lockedCurrent(id, expectedVersion);
        access.requireAt(permission, current.siteId());
        String next = ProductLifecycle.next(current.status(), action);
        audit.open("product." + action.name().toLowerCase(Locale.ROOT));
        setStatus(current, next, action, reason);
        return get(id);
    }

    private void setStatus(Product current, String next, ProductLifecycle.Action action, @Nullable String reason) {
        audit.record(MasterDataSupport.actor(), new AuditEventDraft(action.name(), RECORD_TYPE, current.id(),
                current.version() + 1, current.code(), current.siteId(),
                List.of(new FieldChange("status", current.status(), next)), current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.product SET status = ?, version = version + 1, updated_at = clock_timestamp(),
                       updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(next, CurrentUserHolder.require().userId(), current.id(), current.version())
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
    }

    /** The user who last created or edited the content (from the audit trail, the authoritative history). */
    private @Nullable UUID lastContentEditor(UUID id) {
        return jdbc.sql("""
                SELECT actor_user_id FROM audit.audit_event
                 WHERE record_type = ? AND record_id = ? AND action IN ('CREATE', 'UPDATE')
                 ORDER BY seq DESC LIMIT 1
                """)
            .params(RECORD_TYPE, id)
            .query(UUID.class)
            .optional()
            .orElse(null);
    }

    private Product lockedCurrent(UUID id, long expectedVersion) {
        Product current = visible(id, true);
        if (current.version() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        return current;
    }

    /** The product if it exists and is inside the user's read scope; otherwise 404 (no existence leak). */
    private Product visible(UUID id, boolean forUpdate) {
        Product p = jdbc.sql("SELECT " + COLUMNS + " FROM app.product WHERE id = ? AND deleted_at IS NULL"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param(id)
                .query(ROW)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Product"));
        if (!access.hasAt("masterdata.view", p.siteId())) {
            throw ApiException.notFound("Product");
        }
        return p;
    }

    private void requireActiveSite(UUID siteId) {
        boolean ok = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.site WHERE id = ? AND active AND deleted_at IS NULL)")
                .param(siteId).query(Boolean.class).single();
        if (!ok) {
            Validation v = new Validation();
            v.add("siteId", "UNKNOWN", "no active site with this id");
            v.throwIfAny();
        }
    }

    private void requireUniqueCode(String code, @Nullable UUID exceptId) {
        boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.product WHERE code = ?"
                        + (exceptId == null ? ")" : " AND id <> ?)"))
                .params(exceptId == null ? List.of(code) : List.of(code, exceptId))
                .query(Boolean.class)
                .single();
        if (taken) {
            throw ApiException.duplicate("code", code);
        }
    }
}
