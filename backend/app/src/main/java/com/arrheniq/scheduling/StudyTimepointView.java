package com.arrheniq.scheduling;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One time point of a study's schedule (URS-SCH-001/002/004/007): when it is due, why the date moved, the pull
 * window (± {@code scheduling.pull_window_days}) and its status.
 */
public record StudyTimepointView(UUID id, int seq, String label, long value, String unit, LocalDate calculatedDate,
                                 LocalDate pullDate, @Nullable String ruleApplied, LocalDate windowFrom,
                                 LocalDate windowTo, int unitsPlanned, String status) { }
