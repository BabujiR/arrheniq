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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Requirement("URS-MD-003")
class SpecRulesTest {

    private static final UUID ASSAY = UUID.randomUUID();
    private static final UUID IMPURITY = UUID.randomUUID();
    private static final UUID APPEARANCE = UUID.randomUUID();
    private static final UUID OLD = UUID.randomUUID();

    private static final Map<UUID, SpecRules.LibraryTest> LIBRARY = Map.of(
            ASSAY, new SpecRules.LibraryTest(ASSAY, "ASSAY", "Assay", "ACTIVE", "NUMERIC", "%LC", 2, 1, true),
            IMPURITY, new SpecRules.LibraryTest(IMPURITY, "IMP-B", "Impurity B", "ACTIVE", "NUMERIC", "%", 1, 2, true),
            APPEARANCE, new SpecRules.LibraryTest(APPEARANCE, "APP", "Appearance", "ACTIVE", "TEXT", null, 1, null,
                    false),
            OLD, new SpecRules.LibraryTest(OLD, "OLD", "Old test", "RETIRED", "NUMERIC", null, 1, null, true));

    private static SpecTestInput numeric(UUID id, String lower, String upper) {
        return new SpecTestInput(id, null, null, null, null, lower == null ? null : new BigDecimal(lower),
                upper == null ? null : new BigDecimal(upper), null, null, null, null);
    }

    @Test
    void twoSidedOneSidedAndTextLimitsWithLibraryDefaults() {
        List<SpecTest> tests = SpecRules.validate(List.of(numeric(ASSAY, "90.0", "110.0"), numeric(IMPURITY, null, "0.5"),
                new SpecTestInput(APPEARANCE, "Description", null, null, null, null, null, "White, round tablet",
                        null, null, null)), LIBRARY, Set.of());
        assertEquals(3, tests.size());
        SpecTest assay = tests.get(0);
        assertEquals("Assay", assay.displayName());
        assertEquals("%LC", assay.unitCode());
        assertEquals(2, assay.replicates());
        assertEquals(10, assay.sortOrder());
        assertNull(tests.get(1).lowerLimit(), "NMT 0.5 % is one-sided");
        assertEquals("Description", tests.get(2).displayName());
        assertEquals("White, round tablet", tests.get(2).textLimit());
        assertNull(tests.get(2).unitCode());
    }

    @Test
    void inconsistentLimitsAreRejectedWithTheirPosition() {
        ApiException e = assertThrows(ApiException.class, () -> SpecRules.validate(List.of(
                numeric(ASSAY, "110", "90"),
                new SpecTestInput(IMPURITY, null, null, null, null, null, new BigDecimal("0.5"), "NMT", null,
                        new BigDecimal("0.6"), null),
                new SpecTestInput(APPEARANCE, null, "%", null, null, new BigDecimal("1"), null, null, null, null,
                        null),
                numeric(ASSAY, "90", "110"),
                numeric(OLD, null, null),
                numeric(UUID.randomUUID(), null, null)), LIBRARY, Set.of()));
        List<String> fields = e.errors().stream().map(err -> err.field() + ":" + err.code()).toList();
        assertTrue(fields.contains("tests[0].lowerLimit:ORDER"), fields.toString());
        assertTrue(fields.contains("tests[1].textLimit:NOT_ALLOWED"), fields.toString());
        assertTrue(fields.contains("tests[1].alertUpper:OUTSIDE_LIMITS"), fields.toString());
        assertTrue(fields.contains("tests[2].unitCode:NOT_ALLOWED") && fields.contains("tests[2].lowerLimit:NOT_ALLOWED"),
                fields.toString());
        assertTrue(fields.contains("tests[3].testMethodId:DUPLICATE"), fields.toString());
        assertTrue(fields.contains("tests[4].testMethodId:RETIRED"), fields.toString());
        assertTrue(fields.contains("tests[5].testMethodId:UNKNOWN"), fields.toString());
    }

    @Test
    void aRetiredTestMayStayWhenThePreviousVersionHadIt() {
        List<SpecTest> tests = SpecRules.validate(List.of(numeric(OLD, null, "5")), LIBRARY, Set.of(OLD));
        assertEquals("RETIRED", tests.getFirst().testStatus());
    }
}
