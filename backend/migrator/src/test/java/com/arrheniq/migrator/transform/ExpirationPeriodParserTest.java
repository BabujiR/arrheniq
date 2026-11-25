package com.arrheniq.migrator.transform;

import com.arrheniq.migrator.transform.ExpirationPeriodParser.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** URS-MD-002, URS-MIG-001; BR-007; legacy-mapping.md §3. */
class ExpirationPeriodParserTest {

    private final ExpirationPeriodParser parser = new ExpirationPeriodParser();

    @Test
    void monthsAndYears() {
        var r = parser.parse("24 Months");
        assertEquals(Status.ESTABLISHED, r.status());
        assertEquals(24, r.value());
        assertEquals("MONTH", r.unit());
        assertEquals(36, parser.parse("3 years").value());
        assertEquals(18, parser.parse("18M").value());
        assertEquals(12, parser.parse("12 mos.").value());
    }

    @Test
    void weeksAndDays() {
        assertEquals("WEEK", parser.parse("6 weeks").unit());
        assertEquals(28, parser.parse("28 days").value());
    }

    @Test
    void legacyKeywords() {
        assertEquals(Status.TO_BE_DETERMINED, parser.parse("To be determined").status());
        assertEquals(Status.TO_BE_DETERMINED, parser.parse("Not established").status());
        assertEquals(Status.NOT_APPLICABLE, parser.parse("Not applicable").status());
        assertEquals(Status.TO_BE_DETERMINED, parser.parse("").status());
    }

    @Test
    void garbageIsUnparseableNotGuessed() {
        var r = parser.parse("two years");
        assertEquals(Status.UNPARSEABLE, r.status());
        assertNull(r.value());
        assertEquals(Status.UNPARSEABLE, parser.parse("0 months").status());
    }
}
