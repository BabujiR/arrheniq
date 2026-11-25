package com.arrheniq.kernel.audit;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a command handler supplies to the audit kernel. Everything else (tenant, actor, sequence,
 * timestamp, hashes, correlation id) is filled in by {@link AuditService}, never by the client.
 *
 * @param action        e.g. CREATE, UPDATE, LOGICAL_DELETE, STATUS_CHANGE, SIGN
 * @param recordType    e.g. PRODUCT, TEST_RESULT
 * @param statusBefore  record status before the change (drives {@link ReasonPolicy}); null for new records
 */
public record AuditEventDraft(
        String action,
        String recordType,
        UUID recordId,
        Long recordVersion,
        String businessKey,
        UUID siteId,
        List<FieldChange> changes,
        String statusBefore,
        String reason,
        String reasonCode) {

    public AuditEventDraft {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(recordType, "recordType");
        changes = changes == null ? List.of() : List.copyOf(changes);
        if (reason != null && reason.isBlank()) {
            reason = null;
        }
    }
}
