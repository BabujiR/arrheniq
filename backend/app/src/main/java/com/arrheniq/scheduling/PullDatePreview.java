package com.arrheniq.scheduling;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The pull dates a protocol version would give for an on-stability date, with the site rules that produced
 * them (URS-SCH-001/002). Nothing is stored: studies (S2.2) generate and store their own schedule.
 */
public record PullDatePreview(String algorithm, UUID protocolVersionId, UUID siteId, LocalDate onStabilityDate,
                              Rules rules, List<Line> pullDates) {

    public record Rules(String saturday, String sunday, String holiday, List<LocalDate> holidays) { }

    public record Line(String label, long value, String unit, LocalDate calculatedDate, LocalDate pullDate,
                       @Nullable String ruleApplied) { }
}
