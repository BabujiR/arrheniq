package com.arrheniq.study;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/** A study in lists: identity, product and lot, condition, protocol and specification versions, status. */
public record StudySummary(UUID id, String studyCode, @Nullable UUID studyGroupId, UUID siteId, UUID productId,
                           String productCode, String productName, String lotNumber, String conditionCode,
                           String conditionLabel, UUID protocolId, String protocolNumber, UUID protocolVersionId,
                           int protocolVersionNo, @Nullable String studyType, UUID specVersionId, String specNumber,
                           int specVersionNo, LocalDate onStabilityDate, String status,
                           @Nullable LocalDate nextPullDate) { }
