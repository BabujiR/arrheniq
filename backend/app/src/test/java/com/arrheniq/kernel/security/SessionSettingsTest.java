package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Requirement("URS-IAM-004")
class SessionSettingsTest {

    private static final Duration IDLE = Duration.ofMinutes(30);
    private static final Duration LOGOFF = Duration.ofMinutes(60);

    @Test
    void tenantValuesAreUsed() {
        SessionSettings.Times t = SessionSettings.resolve("15", "45", IDLE, LOGOFF);
        assertEquals(Duration.ofMinutes(15), t.idleLock());
        assertEquals(Duration.ofMinutes(45), t.logoff());
    }

    @Test
    void missingOrInvalidValuesFallBackToDefaults() {
        SessionSettings.Times t = SessionSettings.resolve(null, "\"abc\"", IDLE, LOGOFF);
        assertEquals(IDLE, t.idleLock());
        assertEquals(LOGOFF, t.logoff());
    }

    @Test
    void valuesAreBoundedAndLogoffIsNeverShorterThanTheIdleLock() {
        SessionSettings.Times t = SessionSettings.resolve("0", "100000", IDLE, LOGOFF);
        assertEquals(Duration.ofMinutes(1), t.idleLock());
        assertEquals(Duration.ofHours(8), t.logoff());
        assertEquals(Duration.ofMinutes(40), SessionSettings.resolve("40", "20", IDLE, LOGOFF).logoff());
    }
}
