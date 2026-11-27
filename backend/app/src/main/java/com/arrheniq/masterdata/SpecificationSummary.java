package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A specification in lists: its product, the effective version (if any; its id is what a study refers to) and the version being worked on
 * (DRAFT or IN_REVIEW, if any).
 */
public record SpecificationSummary(UUID id, String specNumber, String title, UUID productId, String productCode,
                                   String productName, UUID siteId, @Nullable Integer effectiveVersionNo,
                                   @Nullable LocalDate effectiveFrom, @Nullable UUID openVersionId,
                                   @Nullable Integer openVersionNo, @Nullable String openStatus,
                                   @Nullable UUID effectiveVersionId) {
}
