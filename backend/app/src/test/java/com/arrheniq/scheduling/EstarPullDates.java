package com.arrheniq.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Test-only port of eSTAR's pull-date calculation, line by line, to measure parity (golden-data plan §4: "exact,
 * or a documented intentional fix"). Source: {@code EAPharmicsUI/.../newstudy/NewStudyConditionData.mxml}
 * {@code dateAddStandardIntervals} (L957–1086), {@code getNumberOfDaysInMonth} (L1088) and
 * {@code util/DateUtility.as} {@code addMonths}/{@code addDays}. Flex {@code Date} behaves like JavaScript's:
 * {@code setMonth}/{@code setDate} roll over (31 Jan → "31 Feb" → 3 Mar).
 */
final class EstarPullDates {

    private EstarPullDates() { }

    /**
     * @param saturday {@code L}, {@code F} or {@code M} ({@code MOVE SATURDAY PULL DATE})
     * @param sunday   the same for Sunday
     */
    static LocalDate pullDate(LocalDate start, long value, String unit, char saturday, char sunday) {
        int startDay = start.getDayOfMonth();
        LocalDate r;
        switch (unit) {
            case "MONTH" -> {
                r = jsDate(start.getYear(), start.getMonthValue() - 1 + value, startDay);   // DateUtility.addMonths
                if (startDay == 30 || startDay == 31) {
                    // the legacy table is 1-based but receives Flex's 0-based getMonth() (BR-026)
                    int numberOfDays = daysInMonth(r.getMonthValue() - 1, r.getYear());
                    if (!(numberOfDays <= startDay)) {
                        r = jsDate(r.getYear(), r.getMonthValue() - 1, startDay);                    // setDate(startDay)
                    }
                }
            }
            case "WEEK" -> r = start.plusDays(7 * value);
            case "DAY" -> r = start.plusDays(value);
            default -> throw new IllegalArgumentException(unit);
        }
        DayOfWeek day = r.getDayOfWeek();     // read once, before any move
        if (day == DayOfWeek.SATURDAY && saturday == 'F') {
            r = r.minusDays(1);
        }
        if (day == DayOfWeek.SATURDAY && saturday == 'M') {
            r = r.plusDays(2);
        }
        if (day == DayOfWeek.SUNDAY && sunday == 'F') {
            r = r.minusDays(2);
        }
        if (day == DayOfWeek.SUNDAY && sunday == 'M') {
            r = r.plusDays(1);
        }
        // BR-028: after the weekend move, 1 March in a "non-leap" year becomes 28 February
        if (!isLeap(r.getYear()) && r.getMonthValue() == 3 && r.getDayOfMonth() == 1) {
            r = LocalDate.of(r.getYear(), 2, 28);
        }
        return r;
    }

    /** JavaScript/Flex {@code new Date(year, month0, day)} with rollover. */
    private static LocalDate jsDate(int year, long month0, int day) {
        return LocalDate.of(year + (int) Math.floorDiv(month0, 12), Math.floorMod(month0, 12) + 1, 1)
                .plusDays(day - 1L);
    }

    private static int daysInMonth(int month, int year) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> LocalDate.of(year, 1, 1).isLeapYear() ? 29 : 28;   // DateValidator on 02/29/year
            default -> 0;
        };
    }

    /** {@code (y % 4 == 0) && (!(y % 100 == 0) || (y % 400))}: wrong for 2000 and 2100, right for 1901–2099. */
    private static boolean isLeap(int y) {
        return y % 4 == 0 && (y % 100 != 0 || y % 400 != 0);
    }
}
