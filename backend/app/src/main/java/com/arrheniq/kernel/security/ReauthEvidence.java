package com.arrheniq.kernel.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Proof that the signed-in user just re-entered their credentials at the IdP: the IdP's
 * {@code auth_time} after the request was made. Single use (consumed by the signature).
 */
public record ReauthEvidence(UUID tenantId, UUID userId, ReauthRequest.Purpose purpose, Instant authTime,
                             Instant verifiedAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
