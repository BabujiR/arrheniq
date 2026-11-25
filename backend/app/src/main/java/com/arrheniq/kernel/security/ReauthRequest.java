package com.arrheniq.kernel.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pending re-authentication, stored in the session when the user starts it.
 *
 * @param purpose     SIGN (e-signature, URS-ESG-002) or UNLOCK (idle lock, URS-IAM-004)
 * @param requestedAt server time when the re-authentication was started
 * @param returnTo    same-origin path to return to afterwards
 */
public record ReauthRequest(UUID tenantId, UUID userId, Purpose purpose, Instant requestedAt, String returnTo)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Purpose { SIGN, UNLOCK }

    public ReauthRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
