package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Requirement("URS-MD-001")
class TestMethodRulesTest {

    static TestMethodInput numeric(String code, BigDecimal lower, BigDecimal upper) {
        return new TestMethodInput(code, "Assay (HPLC)", "USP <621>", "L-12", "numeric", "% LC", 3, 1, lower, upper,
                null, null, null, null, null, null);
    }

    @Test
    void validNumericTestIsNormalised() {
        TestMethodInput.Clean c = TestMethodRules.validate(numeric(" assay-01 ", new BigDecimal("90.0"),
                new BigDecimal("110.00")));
        assertEquals("ASSAY-01", c.code());
        assertEquals("NUMERIC", c.resultsFormat());
        assertEquals(0, new BigDecimal("90").compareTo(c.defaultLowerLimit()));
        assertEquals(3, c.replicates());
        assertTrue(c.trendable());
    }

    @Test
    void lowerLimitAboveUpperIsRejected() {
        assertEquals(List.of("defaultLowerLimit:ORDER"),
                codes(() -> TestMethodRules.validate(numeric("A1", new BigDecimal("111"), new BigDecimal("110")))));
    }

    @Test
    void textTestMustNotCarryNumericAttributes() {
        TestMethodInput in = new TestMethodInput("APPEAR", "Appearance", null, null, "TEXT", "mg", null, 2,
                BigDecimal.ONE, null, "White round tablet", null, null, null, null, null);
        List<String> errors = codes(() -> TestMethodRules.validate(in));
        assertTrue(errors.contains("unitCode:NOT_ALLOWED"), errors.toString());
        assertTrue(errors.contains("decimals:NOT_ALLOWED"), errors.toString());
        assertTrue(errors.contains("defaultLowerLimit:NOT_ALLOWED"), errors.toString());
    }

    @Test
    void allProblemsAreReportedAtOnce() {
        TestMethodInput in = new TestMethodInput("bad code!", " ", null, null, "PICTURE", null, 0, 11, null, null,
                null, null, null, null, null, null);
        List<String> errors = codes(() -> TestMethodRules.validate(in));
        assertTrue(errors.containsAll(List.of("code:FORMAT", "name:REQUIRED", "resultsFormat:INVALID_VALUE",
                "replicates:OUT_OF_RANGE", "decimals:OUT_OF_RANGE")), errors.toString());
    }

    @Test
    void textLimitIsKeptForTextTests() {
        TestMethodInput in = new TestMethodInput("APPEAR", "Appearance", null, null, "TEXT", null, null, null, null,
                null, "  White round tablet ", null, null, null, null, false);
        TestMethodInput.Clean c = TestMethodRules.validate(in);
        assertEquals("White round tablet", c.defaultTextLimit());
        assertNull(c.decimals());
        assertFalse(c.trendable());
    }

    static List<String> codes(Runnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(400, e.status());
        return e.errors().stream().map(f -> f.field() + ":" + f.code()).collect(Collectors.toList());
    }
}
