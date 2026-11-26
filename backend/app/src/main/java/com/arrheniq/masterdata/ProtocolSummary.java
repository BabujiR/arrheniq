package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/** A protocol in lists: its product, the approved version (if any) and the version being worked on (if any). */
public record ProtocolSummary(UUID id, String protocolNumber, String name, UUID productId, String productCode,
                              String productName, UUID siteId, @Nullable Integer approvedVersionNo,
                              @Nullable LocalDate approvedFrom, @Nullable String studyType,
                              @Nullable String conditionCode, @Nullable UUID openVersionId,
                              @Nullable Integer openVersionNo, @Nullable String openStatus) {
}
