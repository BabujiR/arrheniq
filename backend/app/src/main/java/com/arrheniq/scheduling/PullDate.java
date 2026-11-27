package com.arrheniq.scheduling;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * One calculated pull date.
 *
 * @param calculatedDate the pure calendar result (BR-025/026), stored as {@code study_timepoint.calculated_date}
 * @param scheduledDate  after the weekend/holiday rule, stored as {@code scheduled_date}
 * @param ruleApplied    why they differ, e.g. {@code SUNDAY_TO_PREVIOUS_WORKDAY}; null when they don't
 */
public record PullDate(long value, String unit, LocalDate calculatedDate, LocalDate scheduledDate,
                       @Nullable String ruleApplied) { }
