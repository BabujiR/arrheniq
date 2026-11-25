package com.arrheniq.migrator.transform;

import com.arrheniq.migrator.transform.ParsedValue.Qualifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** URS-MIG-001, URS-RES-002; legacy-mapping.md §7. */
class ResultValueParserTest {

    private final ResultValueParser parser = new ResultValueParser(Map.of("NMT", Qualifier.LT));

    @Test
    void blankIsEmpty() {
        assertTrue(parser.parse("   ", false).isEmpty());
        assertTrue(parser.parse(null, false).isEmpty());
    }

    @Test
    void plainNumbersIncludingNegativeAndExponent() {
        assertEquals(new BigDecimal("98.7"), parser.parse(" 98.7 ", false).orElseThrow().numericValue());
        assertEquals(new BigDecimal("-1.25"), parser.parse("-1.25", false).orElseThrow().numericValue());
        assertEquals(new BigDecimal("1.5E-3"), parser.parse("1.5E-3", false).orElseThrow().numericValue());
        assertEquals(new BigDecimal(".5"), parser.parse(".5", false).orElseThrow().numericValue());
    }

    @Test
    void comparatorsBecomeQualifiedValues() {
        ParsedValue lt = parser.parse("< 0.05", false).orElseThrow();
        assertEquals(Qualifier.LT, lt.qualifier());
        assertEquals(new BigDecimal("0.05"), lt.numericValue());
        ParsedValue gt = parser.parse(">100", false).orElseThrow();
        assertEquals(Qualifier.GT, gt.qualifier());
        assertEquals(new BigDecimal("100"), gt.numericValue());
    }

    @Test
    void abbreviationsBecomeQualifiersOnly() {
        assertEquals(Qualifier.ND, parser.parse("n.d.", false).orElseThrow().qualifier());
        assertEquals(Qualifier.LOQ, parser.parse("<LOQ", false).orElseThrow().qualifier());
        assertEquals(Qualifier.LOQ, parser.parse("BQL", false).orElseThrow().qualifier());
        assertNull(parser.parse("ND", false).orElseThrow().numericValue());
    }

    @Test
    void tenantAbbreviationsTakePrecedence() {
        assertEquals(Qualifier.LT, parser.parse("nmt", false).orElseThrow().qualifier());
    }

    @Test
    void textTestsKeepTextVerbatim() {
        ParsedValue v = parser.parse("Conforms", true).orElseThrow();
        assertEquals("Conforms", v.textValue());
        assertTrue(v.parsed());
    }

    @Test
    void unknownInputIsPreservedAndFlaggedNotCoerced() {
        ParsedValue v = parser.parse("98,7", false).orElseThrow();   // decimal comma
        assertFalse(v.parsed());
        assertEquals("98,7", v.textValue());
        assertEquals("98,7", v.rawInput());
        assertNull(v.numericValue());
    }
}
