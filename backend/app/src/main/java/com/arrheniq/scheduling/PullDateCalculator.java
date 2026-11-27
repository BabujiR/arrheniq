package com.arrheniq.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/**
 * The pull-date engine (URS-SCH-001/002): deterministic, no clock, no database.
 *
 * <ol>
 *   <li><b>Calendar date</b> = on-stability date + the time point: months with end-of-month clamping (31 Jan +
 *       1 month = 28/29 Feb, 31 Mar + 1 month = 30 Apr), weeks as 7 days, days.</li>
 *   <li><b>Pull date</b>: if the calendar date is a site holiday, the holiday rule applies; otherwise, on a
 *       Saturday or Sunday, that day's rule. {@link DateRule#MOVE_BEFORE} and {@link DateRule#MOVE_AFTER} go to the
 *       nearest working day (Monday to Friday, not a holiday) in that direction, so a moved date never lands on a
 *       weekend or holiday; {@link DateRule#LEAVE} keeps the date.</li>
 * </ol>
 *
 * <p>Compared with eSTAR this is the same in every case except two documented corrections
 * ({@code docs/delivery/s2-pull-dates.md}): start days 29–31 no longer overflow into the next month, and the legacy
 * "1 March becomes 28 February" patch (BR-028) is gone. Invalid input fails loudly (ADR 0011).
 */
public final class PullDateCalculator {

    /** Algorithm id and version (ADR 0011); stored with every generated schedule. A change means a new version. */
    public static final String ALGORITHM = "schedule.pull-dates@1.0.0";

    /** Values above this are refused: 10000 months is already ~833 years. */
    public static final long MAX_VALUE = 10_000;

    /** How far a moved date may travel before the calendar is considered misconfigured. */
    private static final int MAX_MOVE_DAYS = 31;

    private PullDateCalculator() { }

    public static PullDate calculate(LocalDate onStabilityDate, long value, String unit, SchedulingRules rules) {
        String u = unit.trim().toUpperCase(Locale.ROOT);
        LocalDate calculated = calendarDate(onStabilityDate, value, u);
        DateRule rule;
        String reason;
        if (rules.isHoliday(calculated)) {
            rule = rules.holiday();
            reason = "HOLIDAY";
        } else if (calculated.getDayOfWeek() == DayOfWeek.SATURDAY) {
            rule = rules.saturday();
            reason = "SATURDAY";
        } else if (calculated.getDayOfWeek() == DayOfWeek.SUNDAY) {
            rule = rules.sunday();
            reason = "SUNDAY";
        } else {
            return new PullDate(value, u, calculated, calculated, null);
        }
        if (rule == DateRule.LEAVE) {
            return new PullDate(value, u, calculated, calculated, null);
        }
        int step = rule == DateRule.MOVE_BEFORE ? -1 : 1;
        LocalDate d = calculated;
        for (int i = 0; i < MAX_MOVE_DAYS; i++) {
            d = d.plusDays(step);
            if (isWorkingDay(d, rules)) {
                return new PullDate(value, u, calculated, d,
                        reason + (step < 0 ? "_TO_PREVIOUS_WORKDAY" : "_TO_NEXT_WORKDAY"));
            }
        }
        throw new IllegalArgumentException("no working day within " + MAX_MOVE_DAYS + " days "
                + (step < 0 ? "before " : "after ") + calculated + "; check the site's holiday calendar");
    }

    /** Step 1 only: the calendar date of a time point (BR-025/026). */
    public static LocalDate calendarDate(LocalDate onStabilityDate, long value, String unit) {
        if (value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("time point " + value + " is outside 0.." + MAX_VALUE);
        }
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "MONTH" -> onStabilityDate.plusMonths(value);    // clamps to the last day of a shorter month
            case "WEEK" -> onStabilityDate.plusWeeks(value);
            case "DAY" -> onStabilityDate.plusDays(value);
            default -> throw new IllegalArgumentException("unknown time unit '" + unit + "' (DAY, WEEK, MONTH)");
        };
    }

    public static boolean isWorkingDay(LocalDate date, SchedulingRules rules) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !rules.isHoliday(date);
    }
}
