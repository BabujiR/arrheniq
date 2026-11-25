package com.arrheniq.kernel.audit;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Requirement("URS-AUD-003")
class CanonicalJsonTest {

    @Test
    void keysAreSortedAndOutputHasNoWhitespace() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 1);
        m.put("a", "x");
        m.put("C", true);
        assertEquals("{\"C\":true,\"a\":\"x\",\"b\":1}", CanonicalJson.write(m));
    }

    @Test
    void insertionOrderDoesNotChangeOutput() {
        Map<String, Object> m1 = new LinkedHashMap<>();
        m1.put("x", 1);
        m1.put("y", 2);
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("y", 2);
        m2.put("x", 1);
        assertEquals(CanonicalJson.write(m1), CanonicalJson.write(m2));
    }

    @Test
    void decimalsAreNormalisedExactly() {
        assertEquals("[1.5,100,0,-0.001]", CanonicalJson.write(Arrays.asList(
                new BigDecimal("1.50"), new BigDecimal("1E+2"), new BigDecimal("0.000"), new BigDecimal("-0.0010"))));
    }

    @Test
    void floatingPointIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CanonicalJson.write(List.of(1.5d)));
    }

    @Test
    void stringsAreEscapedPerRfc8259() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001é\"", CanonicalJson.write("a\"b\\c\nd\u0001é"));
    }

    @Test
    void timeTypesUseMicrosecondUtc() {
        assertEquals("\"2026-11-25T18:30:00.123456Z\"",
                CanonicalJson.write(Instant.parse("2026-11-25T18:30:00.123456789Z")));
        assertEquals("\"2027-01-31\"", CanonicalJson.write(LocalDate.of(2027, 1, 31)));
        assertEquals("\"11111111-1111-1111-1111-111111111111\"",
                CanonicalJson.write(UUID.fromString("11111111-1111-1111-1111-111111111111")));
    }

    @Test
    void nullsAreExplicit() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", null);
        assertEquals("{\"k\":null}", CanonicalJson.write(m));
    }
}
