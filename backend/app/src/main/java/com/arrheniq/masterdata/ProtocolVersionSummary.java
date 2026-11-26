package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version in a protocol's history. */
public record ProtocolVersionSummary(UUID id, int versionNo, String status, String studyType,
                                     @Nullable String conditionCode, @Nullable LocalDate effectiveFrom,
                                     @Nullable String changeSummary, int timepointCount, int testCount,
                                     Instant createdAt, @Nullable Instant updatedAt) {
}
