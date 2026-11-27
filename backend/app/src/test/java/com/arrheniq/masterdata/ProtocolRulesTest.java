package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Requirement({"URS-PRO-001", "URS-PRO-002", "URS-PRO-003", "URS-PRO-007"})
class ProtocolRulesTest {

    private static final UUID ASSAY = UUID.randomUUID();
    private static final UUID WATER = UUID.randomUUID();
    private static final UUID OLD = UUID.randomUUID();
    private static final Map<UUID, SpecRules.LibraryTest> LIBRARY = Map.of(
            ASSAY, new SpecRules.LibraryTest(ASSAY, "ASSAY", "Assay", "ACTIVE", "NUMERIC", "%LC", 2, 1, true),
            WATER, new SpecRules.LibraryTest(WATER, "KF", "Water (KF)", "ACTIVE", "NUMERIC", "%", 1, 2, true),
            OLD, new SpecRules.LibraryTest(OLD, "OLD", "Old", "RETIRED", "NUMERIC", null, 1, null, true));
    private static final Set<String> CONDITIONS = Set.of("25C60RH", "40C75RH");

    private static ProtocolInput.TimePointInput tp(String value, String unit, int units) {
        return new ProtocolInput.TimePointInput(new BigDecimal(value), unit, null, units);
    }

    @Test
    void timePointsAreSortedChronologicallyAndTheGridFollows() {
        // sent out of order: 12M, 0M, 2W, 3M
        ProtocolContent c = ProtocolRules.validate(new ProtocolInput("long_term", "25C60RH", 10, "BOTTLE", null,
                List.of(tp("12", "MONTH", 4), tp("0", "MONTH", 6), tp("2", "WEEK", 2), tp("3", "month", 4)),
                List.of(new ProtocolInput.TestInput(ASSAY, List.of(0, 1, 3)),
                        new ProtocolInput.TestInput(WATER, List.of(1, 0)))), LIBRARY, Set.of(), CONDITIONS);
        assertEquals("LONG_TERM", c.studyType());
        assertEquals(List.of("Initial", "2W", "3M", "12M"), c.timepoints().stream().map(ProtocolContent.TimePoint::label)
                .toList());
        assertEquals(List.of(0, 2, 3), c.tests().get(0).at(), "assay at 12M, 0M, 3M → Initial, 3M, 12M");
        assertEquals(List.of(0, 3), c.tests().get(1).at());
        assertEquals(26, c.totalUnits(), "16 pulled + 10 reserve");
        assertEquals("0M 3M 12M", c.auditMap().get("grid.ASSAY"));
    }

    @Test
    void mistakesAreReportedWithTheirPosition() {
        ApiException e = assertThrows(ApiException.class, () -> ProtocolRules.validate(new ProtocolInput("WEEKLY",
                "99C", -1, null, null,
                List.of(tp("3", "MONTH", 1), tp("3.0", "MONTH", 1), tp("1", "YEAR", 1), tp("1.5", "MONTH", 1)),
                List.of(new ProtocolInput.TestInput(ASSAY, List.of()), new ProtocolInput.TestInput(ASSAY, List.of(0)),
                        new ProtocolInput.TestInput(WATER, List.of(9)), new ProtocolInput.TestInput(OLD, List.of(0)))),
                LIBRARY, Set.of(), CONDITIONS));
        List<String> f = e.errors().stream().map(x -> x.field() + ":" + x.code()).toList();
        assertTrue(f.contains("studyType:INVALID_VALUE") && f.contains("conditionCode:UNKNOWN")
                && f.contains("reserveUnits:OUT_OF_RANGE"), f.toString());
        assertTrue(f.contains("timepoints[1].value:DUPLICATE"), "3 and 3.0 months are the same: " + f);
        assertTrue(f.contains("timepoints[2].unit:INVALID_VALUE") && f.contains("timepoints[3].value:FORMAT"),
                f.toString());
        assertTrue(f.contains("tests[0].at:REQUIRED") && f.contains("tests[1].testMethodId:DUPLICATE")
                && f.contains("tests[2].at:INVALID_VALUE") && f.contains("tests[3].testMethodId:RETIRED"), f.toString());
    }

    @Test
    void aRetiredTestMayStayInANewVersionOfAProtocolThatHadIt() {
        ProtocolContent c = ProtocolRules.validate(new ProtocolInput("ACCELERATED", null, null, null, null,
                List.of(tp("0", "MONTH", 2)), List.of(new ProtocolInput.TestInput(OLD, List.of(0)))), LIBRARY,
                Set.of(OLD), CONDITIONS);
        assertEquals("RETIRED", c.tests().getFirst().testStatus());
    }

    @Test
    void planWarningsPointAtGaps() {
        ProtocolContent c = ProtocolRules.validate(new ProtocolInput("LONG_TERM", null, 0, null, null,
                List.of(tp("0", "MONTH", 4), tp("6", "MONTH", 0), tp("9", "MONTH", 2)),
                List.of(new ProtocolInput.TestInput(ASSAY, List.of(0, 1)))), LIBRARY, Set.of(), CONDITIONS);
        assertEquals(List.of("No units planned at 6M, although 1 test runs there", "No test runs at 9M"),
                ProtocolService.warnings(c));
    }
}
