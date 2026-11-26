package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Proof that the signed-in user just re-entered their credentials at the IdP: the IdP's
 * {@code auth_time} after the request was made. Single use (consumed by the signature). For SIGN it carries the
 * {@link SigningIntent} fixed before the password was asked, so it can sign only that record version.
 */
public record ReauthEvidence(UUID tenantId, UUID userId, ReauthRequest.Purpose purpose, Instant authTime,
                             Instant verifiedAt, @Nullable SigningIntent intent) {
}
