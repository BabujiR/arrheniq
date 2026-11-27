package com.arrheniq.study;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Requirement("URS-STU-002")
class StudyCodesTest {

    @Test
    void theDefaultPatternIsTwoDigitYearAndFourDigitNumber() {
        assertEquals("260001", StudyCodes.format("YYnnnn", 2026, 1));
        assertEquals("261234", StudyCodes.format("YYnnnn", 2026, 1234));
        assertEquals("2612345", StudyCodes.format("YYnnnn", 2026, 12345), "grows past the width, never wraps");
        assertEquals("000042", StudyCodes.format("YYnnnn", 2000, 42));
    }

    @Test
    void otherPatterns() {
        assertEquals("STB-2026-007", StudyCodes.format("STB-YYYY-nnn", 2026, 7));
        assertEquals("S00099", StudyCodes.format("Snnnnn", 2026, 99));
        assertEquals("2026", StudyCodes.scopeKey("YYnnnn", 2026), "a sequence per year");
        assertEquals("", StudyCodes.scopeKey("Snnnnn", 2026), "one sequence");
    }

    @Test
    void aPatternNeedsExactlyOneNumber() {
        assertThrows(IllegalStateException.class, () -> StudyCodes.validate("YYYY"));
        assertThrows(IllegalStateException.class, () -> StudyCodes.validate("nnYYnn"));
        assertThrows(IllegalStateException.class, () -> StudyCodes.validate(""));
    }
}
