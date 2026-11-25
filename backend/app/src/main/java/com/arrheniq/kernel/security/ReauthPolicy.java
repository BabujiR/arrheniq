package com.arrheniq.kernel.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Verification of re-authentication (ADR 0005, ADR 0016; Part 11 §11.200; URS-ESG-002).
 * We never trust that the IdP honoured {@code prompt=login&max_age=0}; we check the evidence.
 */
public final class ReauthPolicy {

    /** Allowed clock difference between the IdP and the app. */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(5);

    public enum Verdict { OK, DIFFERENT_USER, DIFFERENT_TENANT, NOT_REAUTHENTICATED, MISSING_AUTH_TIME, TOO_LATE }

    private ReauthPolicy() { }

    /**
     * Checks the IdP callback against the pending request: same tenant, same user, and an
     * {@code auth_time} that is not older than the request (the user really typed credentials again).
     */
    public static Verdict verifyCallback(ReauthRequest pending, UUID tenantId, UUID userId, Instant authTime,
                                         Instant now, Duration maxFlowDuration) {
        if (!pending.tenantId().equals(tenantId)) {
            return Verdict.DIFFERENT_TENANT;
        }
        if (!pending.userId().equals(userId)) {
            return Verdict.DIFFERENT_USER;
        }
        if (authTime == null) {
            return Verdict.MISSING_AUTH_TIME;
        }
        if (authTime.isBefore(pending.requestedAt().minus(CLOCK_SKEW))) {
            return Verdict.NOT_REAUTHENTICATED;
        }
        if (now.isAfter(pending.requestedAt().plus(maxFlowDuration))) {
            return Verdict.TOO_LATE;
        }
        return Verdict.OK;
    }

    /** Evidence may be used for a signature only by the same user, for that purpose, within {@code maxAge}. */
    public static boolean isUsable(ReauthEvidence evidence, UUID tenantId, UUID userId,
                                   ReauthRequest.Purpose purpose, Instant now, Duration maxAge) {
        return evidence != null
                && evidence.tenantId().equals(tenantId)
                && evidence.userId().equals(userId)
                && evidence.purpose() == purpose
                && !now.isAfter(evidence.authTime().plus(maxAge));
    }
}
