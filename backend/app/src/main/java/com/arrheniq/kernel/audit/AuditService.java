package com.arrheniq.kernel.audit;

import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The audit kernel (ADR 0004; audit-esignature.md §3–§5). Every GxP write goes through here,
 * <b>in the same transaction</b> as the business change:
 * <ol>
 *   <li>{@link #open(String)} sets {@code app.audit_ctx}; without it the database rejects
 *       writes to GxP tables (ARQ-AUDIT-001)</li>
 *   <li>{@link #record(AuditActor, AuditEventDraft)} enforces the {@link ReasonPolicy}, serialises appends
 *       per tenant (advisory lock), links to the chain tail and inserts the hash-chained event</li>
 * </ol>
 * The database independently re-checks chain contiguity (trigger, V9) and blocks UPDATE/DELETE.
 */
@Service
public class AuditService {

    private static final RowMapper<Tail> TAIL = (rs, _) -> new Tail(rs.getLong("seq"), rs.getBytes("hash"));

    private final JdbcClient jdbc;
    private final ReasonPolicy reasonPolicy;

    @Autowired
    public AuditService(JdbcClient jdbc) {
        this(jdbc, ReasonPolicy.defaults());
    }

    AuditService(JdbcClient jdbc, ReasonPolicy reasonPolicy) {
        this.jdbc = jdbc;
        this.reasonPolicy = reasonPolicy;
    }

    /**
     * Opens the audit context for the current transaction. Returns the correlation id that
     * ties together all audit events written in this unit of work.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String open(String operation) {
        String correlationId = UUID.randomUUID().toString();
        jdbc.sql("SELECT set_config('app.audit_ctx', ?, true)")
            .param(correlationId + "|" + operation)
            .query(String.class)
            .single();
        return correlationId;
    }

    /** Appends one event to the tenant's chain. Must run inside the business transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditActor actor, AuditEventDraft draft) {
        reasonPolicy.check(draft);
        TenantContext ctx = TenantContextHolder.require();
        String correlationId = currentCorrelationId()
                .orElseThrow(() -> new IllegalStateException("ARQ-AUDIT-011: audit context not opened"));

        // serialise appends for this tenant (transaction-scoped lock, released at commit/rollback)
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
            .param(ctx.tenantId().toString())
            .query((_, _) -> 1)
            .single();

        Tail tail = jdbc.sql("SELECT seq, hash FROM audit.audit_event ORDER BY seq DESC LIMIT 1")
            .query(TAIL)
            .optional()
            .orElseThrow(() -> new IllegalStateException(
                    "ARQ-AUDIT-012: tenant has no genesis event (not provisioned)"));

        // DB clock is the time source (microsecond precision). Read after taking the lock so that
        // occurred_at follows seq order even when transactions overlap (now() is tx start time).
        OffsetDateTime now = jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single();

        AuditEvent unsigned = new AuditEvent(ctx.tenantId(), tail.seq() + 1, UUID.randomUUID(), now.toInstant(),
                draft.siteId(), actor.userId(), actor.name(), actor.type(), draft.action(), draft.recordType(),
                draft.recordId(), draft.recordVersion(), draft.businessKey(), draft.changes(), draft.reason(),
                draft.reasonCode(), correlationId, false, null, null);
        byte[] hash = AuditHasher.hash(unsigned, tail.hash());
        AuditEvent event = unsigned.withHashes(tail.hash(), hash);

        jdbc.sql("""
                INSERT INTO audit.audit_event
                  (tenant_id, seq, id, occurred_at, site_id, actor_user_id, actor_name, actor_type, action,
                   record_type, record_id, record_version, business_key, changes, reason, reason_code,
                   correlation_id, legacy, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)
                """)
            .params(event.tenantId(), event.seq(), event.id(), now, event.siteId(), event.actorUserId(),
                    event.actorName(), event.actorType().name(), event.action(), event.recordType(),
                    event.recordId(), event.recordVersion(), event.businessKey(),
                    ChangesCodec.toJson(event.changes()), event.reason(), event.reasonCode(),
                    event.correlationId(), event.legacy(), event.prevHash(), event.hash())
            .update();
        return event;
    }

    /** Loads the current tenant's chain (for verification and audit-trail views) in seq order. */
    @Transactional(readOnly = true)
    public List<AuditEvent> loadChain(long fromSeq, int limit) {
        return jdbc.sql("""
                SELECT tenant_id, seq, id, occurred_at, site_id, actor_user_id, actor_name, actor_type, action,
                       record_type, record_id, record_version, business_key, changes::text AS changes, reason,
                       reason_code, correlation_id, legacy, prev_hash, hash
                  FROM audit.audit_event WHERE seq >= ? ORDER BY seq LIMIT ?
                """)
            .params(fromSeq, limit)
            .query(AuditService::mapEvent)
            .list();
    }

    /**
     * The audit trail of some records (e.g. a protocol and one of its versions), oldest first, for the record history
     * views (URS-AUD-006). Only events at sites the user can see, or without a site, are returned.
     */
    @RequirePermission("audit.view")
    @Transactional(readOnly = true)
    public List<AuditEvent> recordHistory(List<UUID> recordIds, int limit) {
        if (recordIds.isEmpty()) {
            return List.of();
        }
        TenantContext ctx = TenantContextHolder.require();
        return jdbc.sql("""
                SELECT tenant_id, seq, id, occurred_at, site_id, actor_user_id, actor_name, actor_type, action,
                       record_type, record_id, record_version, business_key, changes::text AS changes, reason,
                       reason_code, correlation_id, legacy, prev_hash, hash
                  FROM audit.audit_event
                 WHERE record_id = ANY (CAST(? AS uuid[]))
                   AND (site_id IS NULL OR ? OR site_id = ANY (CAST(? AS uuid[])))
                 ORDER BY seq LIMIT ?
                """)
            .params(recordIds.stream().map(UUID::toString).distinct().toArray(String[]::new), ctx.allSites(),
                    ctx.siteIds().stream().map(UUID::toString).toArray(String[]::new), Math.clamp(limit, 1, 1000))
            .query(AuditService::mapEvent)
            .list();
    }

    private Optional<String> currentCorrelationId() {
        String v = jdbc.sql("SELECT current_setting('app.audit_ctx', true)").query(String.class).optional().orElse(null);
        if (v == null || v.isEmpty()) {
            return Optional.empty();
        }
        int bar = v.indexOf('|');
        return Optional.of(bar > 0 ? v.substring(0, bar) : v);
    }

    private static AuditEvent mapEvent(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long recordVersion = rs.getLong("record_version");
        Long recVersion = rs.wasNull() ? null : recordVersion;
        return new AuditEvent(
                rs.getObject("tenant_id", UUID.class),
                rs.getLong("seq"),
                rs.getObject("id", UUID.class),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getObject("site_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getString("actor_name"),
                AuditActor.Type.valueOf(rs.getString("actor_type")),
                rs.getString("action"),
                rs.getString("record_type"),
                rs.getObject("record_id", UUID.class),
                recVersion,
                rs.getString("business_key"),
                ChangesCodec.parse(rs.getString("changes")),
                rs.getString("reason"),
                rs.getString("reason_code"),
                rs.getString("correlation_id"),
                rs.getBoolean("legacy"),
                rs.getBytes("prev_hash"),
                rs.getBytes("hash"));
    }

    private record Tail(long seq, byte[] hash) { }
}
