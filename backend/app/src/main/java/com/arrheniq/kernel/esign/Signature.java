package com.arrheniq.kernel.esign;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A signature as shown to users (URS-ESG-001 manifestation): printed name, meaning, time, and whether
 * it is still bound to the record's current content.
 *
 * @param status VALID, SUPERSEDED (record changed afterwards) or REVOKED
 */
public record Signature(UUID id, String meaning, UUID signerUserId, String signerName, Instant signedAt,
                        long recordVersion, String status, @Nullable String comment) {
}
