package com.arrheniq.scheduling;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parity with eSTAR (golden-data plan §4): over every on-stability date of 2020–2030, typical time points and all
 * nine weekend-rule combinations, ArrheniQ gives the same pull date as eSTAR except in the two documented
 * corrections (docs/delivery/s2-pull-dates.md):
 * <ul>
 *   <li><b>A</b> start day 29–31 into a shorter month: eSTAR rolls over into the next month (31 Jan + 1M = 3 Mar),
 *       ArrheniQ clamps to the month end (28 Feb) as URS-SCH-001 requires;</li>
 *   <li><b>B</b> eSTAR turns 1 March into 28 February after the weekend move (BR-028), even onto a Sunday;
 *       ArrheniQ keeps the correct date.</li>
 * </ul>
 * Any other difference fails the build.
 */
@Requirement({"URS-SCH-001", "URS-SCH-002"})
class EstarParityTest {

    private static final DateRule[] RULES = {DateRule.LEAVE, DateRule.MOVE_BEFORE, DateRule.MOVE_AFTER};
    private static final char[] ESTAR = {'L', 'F', 'M'};

    @Test
    void sameAsEstarExceptTheDocumentedCorrections() {
        Map<String, Integer> differences = new TreeMap<>();
        int compared = 0;
        for (LocalDate d = LocalDate.parse("2020-01-01"); d.getYear() <= 2030; d = d.plusDays(1)) {
            for (String unit : new String[] {"MONTH", "WEEK", "DAY"}) {
                for (long value : values(unit)) {
                    for (int s = 0; s < 3; s++) {
                        for (int u = 0; u < 3; u++) {
                            compared++;
                            SchedulingRules rules = new SchedulingRules(RULES[s], RULES[u], DateRule.MOVE_AFTER,
                                    Set.of());
                            PullDate ours = PullDateCalculator.calculate(d, value, unit, rules);
                            LocalDate estar = EstarPullDates.pullDate(d, value, unit, ESTAR[s], ESTAR[u]);
                            if (!ours.scheduledDate().equals(estar)) {
                                differences.merge(category(d, value, unit, ours, estar), 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("eSTAR parity: %d cases compared, differences by category: %s%n", compared, differences);
        assertTrue(differences.containsKey("A") && differences.containsKey("B"),
                "both corrections should show up in the sweep: " + differences);
        assertEquals(2, differences.size(), "only the documented corrections: " + differences);
    }

    /** The eSTAR column of the reference cases is what the port gives (so the port and the table agree). */
    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/scheduling/pull-date-reference.csv", numLinesToSkip = 1)
    void referenceCasesRecordEstar(ArgumentsAccessor row) {
        LocalDate estar = row.get(11, LocalDate.class);
        if (estar == null) {
            return;     // site holidays: eSTAR has no holiday calendar
        }
        assertEquals(estar, EstarPullDates.pullDate(row.get(1, LocalDate.class), row.getLong(2), row.getString(3),
                code(row.getString(4)), code(row.getString(5))), row.getString(0));
    }

    private static String category(LocalDate start, long value, String unit, PullDate ours, LocalDate estar) {
        if (unit.equals("MONTH") && start.getDayOfMonth() >= 29
                && ours.calculatedDate().getDayOfMonth() < start.getDayOfMonth()) {
            return "A";
        }
        if (estar.getMonthValue() == 2 && estar.getDayOfMonth() == 28) {
            return "B";
        }
        fail("undocumented difference: " + start + " + " + value + " " + unit + ": ArrheniQ " + ours.scheduledDate()
                + ", eSTAR " + estar);
        return "";
    }

    private static long[] values(String unit) {
        return switch (unit) {
            case "MONTH" -> new long[] {0, 1, 2, 3, 6, 9, 12, 18, 24, 36, 48, 60};
            case "WEEK" -> new long[] {1, 2, 4, 8, 13};
            default -> new long[] {1, 7, 14, 30, 90};
        };
    }

    private static char code(String rule) {
        return ESTAR[DateRule.parse(rule).ordinal()];
    }
}
