package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Server-side session rules (URS-IAM-004; tenant settings {@code session.idle_lock_minutes} and
 * {@code session.logoff_minutes}):
 * <ul>
 *   <li><b>EXPIRED</b>: the absolute session lifetime is over → the session is ended</li>
 *   <li><b>LOCKED</b>: no activity for the idle-lock time (or already locked) → every request except
 *       re-authentication is refused until the same user re-authenticates</li>
 *   <li>Idle <b>logoff</b> is the session's max-inactive interval, enforced by the session store</li>
 * </ul>
 */
public final class SessionPolicy {

    public enum State { ACTIVE, LOCKED, EXPIRED }

    private SessionPolicy() { }

    public static State evaluate(Instant now, @Nullable Instant loginAt, @Nullable Instant lastActivityAt, boolean alreadyLocked,
                                 Duration idleLock, Duration absoluteLifetime) {
        Objects.requireNonNull(now, "now");
        if (loginAt == null || lastActivityAt == null) {
            return State.EXPIRED;   // a session without login bookkeeping is not trusted
        }
        if (!now.isBefore(loginAt.plus(absoluteLifetime))) {
            return State.EXPIRED;
        }
        if (alreadyLocked || !now.isBefore(lastActivityAt.plus(idleLock))) {
            return State.LOCKED;
        }
        return State.ACTIVE;
    }

    /** Avoids a session write on every request: activity is recorded at most once per interval. */
    public static boolean shouldRecordActivity(Instant now, @Nullable Instant lastActivityAt, Duration granularity) {
        return lastActivityAt == null || !now.isBefore(lastActivityAt.plus(granularity));
    }
}
