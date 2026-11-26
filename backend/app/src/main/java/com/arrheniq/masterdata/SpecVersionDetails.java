package com.arrheniq.masterdata;

import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.Signature;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One specification version with its tests, signatures and approval progress.
 *
 * @param version          optimistic-lock version (send back as {@code expectedVersion}; signed as the record version)
 * @param previousVersionId the version before this one, for comparing (null for version 1)
 */
public record SpecVersionDetails(UUID id, long version, SpecificationSummary specification, int versionNo,
                                 String status, @Nullable LocalDate effectiveFrom, @Nullable String changeSummary,
                                 List<SpecTest> tests, @Nullable UUID previousVersionId, List<Signature> signatures,
                                 ApprovalService.Progress approval, Instant createdAt, @Nullable Instant updatedAt) {
}
