package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pending re-authentication, stored in the session when the user starts it.
 *
 * @param purpose     SIGN (e-signature, URS-ESG-002) or UNLOCK (idle lock, URS-IAM-004)
 * @param requestedAt server time when the re-authentication was started
 * @param returnTo    same-origin path to return to afterwards
 * @param intent      for SIGN: the record (and version) the user is about to sign; null for UNLOCK
 */
public record ReauthRequest(UUID tenantId, UUID userId, Purpose purpose, Instant requestedAt, String returnTo,
                            @Nullable SigningIntent intent) {

    public enum Purpose { SIGN, UNLOCK }

    public ReauthRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (purpose == Purpose.SIGN && intent == null) {
            throw new IllegalArgumentException("a SIGN re-authentication needs a signing intent");
        }
        if (purpose == Purpose.UNLOCK && intent != null) {
            throw new IllegalArgumentException("an UNLOCK re-authentication has no signing intent");
        }
    }
}
