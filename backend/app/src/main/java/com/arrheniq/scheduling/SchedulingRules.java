package com.arrheniq.scheduling;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rules one site schedules pulls by: tenant settings {@code scheduling.saturday_rule}, {@code sunday_rule},
 * {@code holiday_rule} (a site may override them) and the site's holiday calendar (sorted, immutable).
 */
public record SchedulingRules(DateRule saturday, DateRule sunday, DateRule holiday, Set<LocalDate> holidays) {

    /** Legacy defaults (BR-027: Saturday = L, Sunday = F) plus holidays moved to the next working day. */
    public static final SchedulingRules DEFAULT =
            new SchedulingRules(DateRule.LEAVE, DateRule.MOVE_BEFORE, DateRule.MOVE_AFTER, Set.of());

    public SchedulingRules {
        holidays = Collections.unmodifiableSortedSet(new TreeSet<>(holidays));
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }
}
