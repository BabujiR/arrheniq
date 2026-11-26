package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One version in a specification's history. */
public record SpecVersionSummary(UUID id, int versionNo, String status, @Nullable LocalDate effectiveFrom,
                                 @Nullable String changeSummary, int testCount, Instant createdAt,
                                 @Nullable Instant updatedAt) {
}
