package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAndReauthPolicyTest {

    private static final Instant LOGIN = Instant.parse("2026-11-25T14:00:00Z");
    private static final Duration IDLE = Duration.ofMinutes(30);
    private static final Duration ABS = Duration.ofHours(12);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final SigningIntent INTENT = new SigningIntent(UUID.randomUUID(), "PRODUCT", UUID.randomUUID(), 1);

    @Test
    @Requirement("URS-IAM-004")
    void sessionLocksAfterIdleTimeAndStaysLocked() {
        assertEquals(SessionPolicy.State.ACTIVE,
                SessionPolicy.evaluate(LOGIN.plusSeconds(1799), LOGIN, LOGIN, false, IDLE, ABS));
        assertEquals(SessionPolicy.State.LOCKED,
                SessionPolicy.evaluate(LOGIN.plusSeconds(1800), LOGIN, LOGIN, false, IDLE, ABS));
        assertEquals(SessionPolicy.State.LOCKED,
                SessionPolicy.evaluate(LOGIN.plusSeconds(10), LOGIN, LOGIN.plusSeconds(5), true, IDLE, ABS));
    }

    @Test
    @Requirement("URS-IAM-004")
    void sessionExpiresAfterAbsoluteLifetimeEvenWhenActive() {
        Instant now = LOGIN.plus(ABS);
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(now, LOGIN, now.minusSeconds(1), false, IDLE, ABS));
        assertEquals(SessionPolicy.State.EXPIRED, SessionPolicy.evaluate(now, null, null, false, IDLE, ABS));
    }

    @Test
    void activityIsRecordedAtMostOncePerInterval() {
        assertFalse(SessionPolicy.shouldRecordActivity(LOGIN.plusSeconds(10), LOGIN, Duration.ofSeconds(30)));
        assertTrue(SessionPolicy.shouldRecordActivity(LOGIN.plusSeconds(30), LOGIN, Duration.ofSeconds(30)));
    }

    @Test
    @Requirement("URS-ESG-002")
    void reauthMustBeSameUserWithFreshAuthTime() {
        ReauthRequest req = new ReauthRequest(TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN, "/studies/1", INTENT);
        Duration flow = Duration.ofMinutes(10);
        assertEquals(ReauthPolicy.Verdict.OK,
                ReauthPolicy.verifyCallback(req, TENANT, USER, LOGIN.plusSeconds(20), LOGIN.plusSeconds(21), flow));
        assertEquals(ReauthPolicy.Verdict.DIFFERENT_USER,
                ReauthPolicy.verifyCallback(req, TENANT, UUID.randomUUID(), LOGIN.plusSeconds(20), LOGIN.plusSeconds(21), flow));
        assertEquals(ReauthPolicy.Verdict.DIFFERENT_TENANT,
                ReauthPolicy.verifyCallback(req, UUID.randomUUID(), USER, LOGIN.plusSeconds(20), LOGIN.plusSeconds(21), flow));
        // IdP silently reused the old SSO session: auth_time is before the request
        assertEquals(ReauthPolicy.Verdict.NOT_REAUTHENTICATED,
                ReauthPolicy.verifyCallback(req, TENANT, USER, LOGIN.minusSeconds(600), LOGIN.plusSeconds(21), flow));
        assertEquals(ReauthPolicy.Verdict.MISSING_AUTH_TIME,
                ReauthPolicy.verifyCallback(req, TENANT, USER, null, LOGIN.plusSeconds(21), flow));
        assertEquals(ReauthPolicy.Verdict.TOO_LATE,
                ReauthPolicy.verifyCallback(req, TENANT, USER, LOGIN.plusSeconds(20), LOGIN.plus(flow).plusSeconds(1), flow));
    }

    @Test
    @Requirement("URS-ESG-002")
    void evidenceIsUsableOnlyForSameUserPurposeAndWithinMaxAge() {
        ReauthEvidence ev = new ReauthEvidence(TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN, LOGIN.plusSeconds(1),
                INTENT);
        Duration maxAge = Duration.ofMinutes(5);
        assertTrue(ReauthPolicy.isUsable(ev, TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN.plusSeconds(299), maxAge));
        assertFalse(ReauthPolicy.isUsable(ev, TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN.plusSeconds(301), maxAge));
        assertFalse(ReauthPolicy.isUsable(ev, TENANT, USER, ReauthRequest.Purpose.UNLOCK, LOGIN.plusSeconds(10), maxAge));
        assertFalse(ReauthPolicy.isUsable(ev, TENANT, UUID.randomUUID(), ReauthRequest.Purpose.SIGN, LOGIN.plusSeconds(10), maxAge));
        assertFalse(ReauthPolicy.isUsable(null, TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN, maxAge));
    }

    @Test
    @Requirement("URS-ESG-002")
    void aSignatureReauthenticationNamesExactlyOneRecordVersion() {
        UUID product = UUID.randomUUID();
        SigningIntent intent = new SigningIntent(UUID.randomUUID(), "PRODUCT", product, 3);
        assertTrue(intent.covers("PRODUCT", product, 3));
        assertFalse(intent.covers("PRODUCT", product, 4), "another version of the same record");
        assertFalse(intent.covers("PRODUCT", UUID.randomUUID(), 3), "another record");
        assertFalse(intent.covers("SPECIFICATION", product, 3), "another record type");

        assertThrows(IllegalArgumentException.class,
                () -> new ReauthRequest(TENANT, USER, ReauthRequest.Purpose.SIGN, LOGIN, "/", null),
                "SIGN without an intent");
        assertThrows(IllegalArgumentException.class,
                () -> new ReauthRequest(TENANT, USER, ReauthRequest.Purpose.UNLOCK, LOGIN, "/", intent),
                "UNLOCK with an intent");
        assertThrows(IllegalArgumentException.class,
                () -> new SigningIntent(UUID.randomUUID(), "product; drop", product, 1));
        assertThrows(IllegalArgumentException.class, () -> new SigningIntent(UUID.randomUUID(), "PRODUCT", product, -1));
    }
}
