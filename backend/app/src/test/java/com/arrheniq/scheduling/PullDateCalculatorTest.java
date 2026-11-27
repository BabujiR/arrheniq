package com.arrheniq.scheduling;

import com.arrheniq.testsupport.Requirement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pull-date engine against the reference cases in {@code scheduling/pull-date-reference.csv}: month-end
 * clamping, leap years, weeks and days, every weekend rule, site holidays (URS-SCH-001/002). Each row also holds
 * what eSTAR gives for the same input, checked by {@link EstarParityTest}.
 */
@Requirement({"URS-SCH-001", "URS-SCH-002"})
class PullDateCalculatorTest {

    @ParameterizedTest(name = "{0}: {12}")
    @CsvFileSource(resources = "/scheduling/pull-date-reference.csv", numLinesToSkip = 1)
    void referenceCases(ArgumentsAccessor row) {
        String id = row.getString(0);
        String unit = row.getString(3);
        SchedulingRules rules = new SchedulingRules(DateRule.parse(row.getString(4)), DateRule.parse(row.getString(5)),
                DateRule.parse(row.getString(6)), parseDates(row.getString(7)));
        PullDate d = PullDateCalculator.calculate(row.get(1, LocalDate.class), row.getLong(2), unit, rules);
        assertEquals(row.get(8, LocalDate.class), d.calculatedDate(), id + " calendar date");
        assertEquals(row.get(9, LocalDate.class), d.scheduledDate(), id + " pull date");
        assertEquals(row.getString(10), d.ruleApplied(), id + " rule");
        assertEquals(unit, d.unit());
    }

    @Test
    void aMovedDateIsAlwaysAWorkingDay() {
        // every day of 2025–2027 at every month offset, with each moving rule and a busy holiday calendar
        Set<LocalDate> holidays = Set.of(LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-25"),
                LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-18"));
        for (DateRule moving : new DateRule[] {DateRule.MOVE_BEFORE, DateRule.MOVE_AFTER}) {
            SchedulingRules rules = new SchedulingRules(moving, moving, moving, holidays);
            for (LocalDate d = LocalDate.parse("2025-01-01"); d.getYear() < 2028; d = d.plusDays(1)) {
                for (long m : new long[] {0, 1, 3, 6, 12, 24}) {
                    PullDate p = PullDateCalculator.calculate(d, m, "MONTH", rules);
                    assertTrue(PullDateCalculator.isWorkingDay(p.scheduledDate(), rules), d + " + " + m + "M");
                    assertTrue(Math.abs(p.scheduledDate().toEpochDay() - p.calculatedDate().toEpochDay()) <= 6,
                            "moves stay close: " + p);
                }
            }
        }
    }

    @Test
    void leaveKeepsTheDateAndAWeekdayIsNeverTouched() {
        SchedulingRules leave = new SchedulingRules(DateRule.LEAVE, DateRule.LEAVE, DateRule.LEAVE,
                Set.of(LocalDate.parse("2026-12-25")));
        IntStream.range(0, 60).forEach(i -> {
            LocalDate d = LocalDate.parse("2026-12-01").plusDays(i);
            PullDate p = PullDateCalculator.calculate(d, 0, "DAY", leave);
            assertEquals(d, p.scheduledDate());
            assertNull(p.ruleApplied());
        });
    }

    @Test
    void invalidInputFailsLoudly() {
        LocalDate d = LocalDate.parse("2026-01-15");
        assertThrows(IllegalArgumentException.class, () -> PullDateCalculator.calculate(d, -1, "MONTH",
                SchedulingRules.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> PullDateCalculator.calculate(d, 10_001, "DAY",
                SchedulingRules.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> PullDateCalculator.calculate(d, 1, "YEAR",
                SchedulingRules.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> DateRule.parse("FRIDAY"));
        // a calendar where everything after the date is a holiday for more than a month
        Set<LocalDate> blocked = IntStream.range(0, 40).mapToObj(i -> d.plusDays(i)).collect(Collectors.toSet());
        SchedulingRules rules = new SchedulingRules(DateRule.LEAVE, DateRule.MOVE_BEFORE, DateRule.MOVE_AFTER, blocked);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PullDateCalculator.calculate(d, 0, "DAY", rules));
        assertTrue(e.getMessage().contains("holiday calendar"), e.getMessage());
    }

    @Test
    void theAlgorithmIsVersioned() {
        assertEquals("schedule.pull-dates@1.0.0", PullDateCalculator.ALGORITHM);
    }

    static Set<LocalDate> parseDates(@Nullable String list) {
        return list == null || list.isBlank() ? Set.of()
                : Arrays.stream(list.split(";")).map(LocalDate::parse).collect(Collectors.toCollection(HashSet::new));
    }
}
