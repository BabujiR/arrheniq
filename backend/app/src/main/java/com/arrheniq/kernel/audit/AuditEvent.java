package com.arrheniq.kernel.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A complete audit event as stored in {@code audit.audit_event} (V9).
 * {@link #hashInput()} defines exactly which fields are covered by the hash chain.
 */
public record AuditEvent(
        UUID tenantId,
        long seq,
        UUID id,
        Instant occurredAt,
        UUID siteId,
        UUID actorUserId,
        String actorName,
        AuditActor.Type actorType,
        String action,
        String recordType,
        UUID recordId,
        Long recordVersion,
        String businessKey,
        List<FieldChange> changes,
        String reason,
        String reasonCode,
        String correlationId,
        boolean legacy,
        byte[] prevHash,
        byte[] hash) {

    public AuditEvent {
        changes = List.copyOf(changes);
        prevHash = prevHash == null ? null : prevHash.clone();
        hash = hash == null ? null : hash.clone();
    }

    @Override
    public byte[] prevHash() {
        return prevHash == null ? null : prevHash.clone();
    }

    @Override
    public byte[] hash() {
        return hash == null ? null : hash.clone();
    }

    /** JSON array stored in the {@code changes} column. */
    public List<Map<String, Object>> changesJson() {
        List<Map<String, Object>> list = new ArrayList<>(changes.size());
        for (FieldChange c : changes) {
            list.add(c.toJsonMap());
        }
        return list;
    }

    /** The fields covered by the hash (canonical JSON v1). Hashes and client_info are excluded. */
    public Map<String, Object> hashInput() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", tenantId);
        m.put("seq", seq);
        m.put("id", id);
        m.put("occurredAt", occurredAt);
        m.put("siteId", siteId);
        m.put("actorUserId", actorUserId);
        m.put("actorName", actorName);
        m.put("actorType", actorType);
        m.put("action", action);
        m.put("recordType", recordType);
        m.put("recordId", recordId);
        m.put("recordVersion", recordVersion);
        m.put("businessKey", businessKey);
        m.put("changes", changesJson());
        m.put("reason", reason);
        m.put("reasonCode", reasonCode);
        m.put("correlationId", correlationId);
        m.put("legacy", legacy);
        return m;
    }

    public AuditEvent withHashes(byte[] newPrevHash, byte[] newHash) {
        return new AuditEvent(tenantId, seq, id, occurredAt, siteId, actorUserId, actorName, actorType, action,
                recordType, recordId, recordVersion, businessKey, changes, reason, reasonCode, correlationId, legacy,
                newPrevHash, newHash);
    }
}
