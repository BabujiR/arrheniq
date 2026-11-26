package com.arrheniq.kernel.esign;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Requirement("URS-MD-004")
class ApprovalLevelTest {

    @Test
    void settingValuesAreReadAsStoredAndFailSafe() {
        assertEquals(ApprovalLevel.DOUBLE, ApprovalLevel.parse("\"DOUBLE\""));
        assertEquals(ApprovalLevel.NONE, ApprovalLevel.parse("none"));
        assertEquals(ApprovalLevel.SINGLE, ApprovalLevel.parse(" \"Single\" "));
        // missing or unknown never weakens approval below one signature
        assertEquals(ApprovalLevel.SINGLE, ApprovalLevel.parse(null));
        assertEquals(ApprovalLevel.SINGLE, ApprovalLevel.parse("\"TRIPLE\""));
        assertEquals(ApprovalLevel.SINGLE, ApprovalLevel.parse(""));
    }

    @Test
    void signaturesRequiredPerLevel() {
        assertEquals(0, ApprovalLevel.NONE.signaturesRequired());
        assertEquals(1, ApprovalLevel.SINGLE.signaturesRequired());
        assertEquals(2, ApprovalLevel.DOUBLE.signaturesRequired());
    }
}
