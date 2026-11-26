package com.arrheniq.kernel.audit;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.security.RequirePermission;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only record history (who did what, when, why, old → new) for the product, specification and protocol pages
 * (URS-AUD-006, the record-level view; filters and export come later). Hashes and correlation ids stay internal;
 * the chain itself is verified by {@link AuditChainVerifier}.
 */
@RestController
public class RecordHistoryController {

    /** One line of a record's history. */
    public record Entry(long seq, Instant occurredAt, String actorName, String actorType, String action,
                        String recordType, @Nullable UUID recordId, @Nullable Long recordVersion,
                        @Nullable String businessKey, List<FieldChange> changes, @Nullable String reason) { }

    private final AuditService audit;

    public RecordHistoryController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit/history")
    @RequirePermission("audit.view")
    public List<Entry> history(@RequestParam("recordId") List<UUID> recordIds,
                               @RequestParam(name = "limit", defaultValue = "500") int limit) {
        if (recordIds.size() > 20) {
            throw ApiException.invalid(List.of(new ApiException.FieldError("recordId", "OUT_OF_RANGE",
                    "at most 20 records")));
        }
        return audit.recordHistory(recordIds, limit).stream()
                .map(e -> new Entry(e.seq(), e.occurredAt(), e.actorName(), e.actorType().name(), e.action(),
                        e.recordType(), e.recordId(), e.recordVersion(), e.businessKey(), e.changes(), e.reason()))
                .toList();
    }
}
