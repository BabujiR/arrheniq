package com.arrheniq.masterdata;

import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.Signature;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One protocol version: content, planned units, warnings, signatures and approval progress.
 *
 * @param version        optimistic-lock version (send back as {@code expectedVersion}; signed as the record version)
 * @param conditionLabel the tenant's label of the intended storage condition
 * @param totalUnits     units needed for the whole study: all time points plus the reserve (URS-PRO-003)
 * @param warnings       plan problems that don't block saving, e.g. a time point with tests but no units planned
 */
public record ProtocolVersionDetails(UUID id, long version, ProtocolSummary protocol, int versionNo, String status,
                                     @Nullable LocalDate effectiveFrom, ProtocolContent content,
                                     @Nullable String conditionLabel, int totalUnits, List<String> warnings,
                                     @Nullable UUID previousVersionId, List<Signature> signatures,
                                     ApprovalService.Progress approval, Instant createdAt,
                                     @Nullable Instant updatedAt) {
}
