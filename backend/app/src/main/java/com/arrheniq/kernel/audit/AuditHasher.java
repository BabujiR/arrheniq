package com.arrheniq.kernel.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * Hash chain rules (ADR 0004; audit-esignature.md §3, §8):
 * <pre>
 *   hash(event)   = SHA-256( UTF-8(canonicalJson(event.hashInput())) || prev_hash )
 *   hash(genesis) = SHA-256( UTF-8("ARRHENIQ-GENESIS|" + tenantId + "|" + occurredAt[µs, UTC]) || 32 zero bytes )
 * </pre>
 * The genesis rule must stay identical to {@code platform.provision_tenant()} in V11.
 */
public final class AuditHasher {

    public static final int HASH_LENGTH = 32;
    public static final String GENESIS_ACTION = "TENANT_PROVISIONED";

    private AuditHasher() { }

    public static byte[] zeroHash() {
        return new byte[HASH_LENGTH];
    }

    public static byte[] hash(AuditEvent event, byte[] prevHash) {
        requireHashLength(prevHash);
        byte[] body = CanonicalJson.write(event.hashInput()).getBytes(StandardCharsets.UTF_8);
        return sha256(body, prevHash);
    }

    public static byte[] genesisHash(UUID tenantId, Instant occurredAt) {
        String text = "ARRHENIQ-GENESIS|" + tenantId + "|" + CanonicalJson.formatInstant(occurredAt);
        return sha256(text.getBytes(StandardCharsets.UTF_8), zeroHash());
    }

    /** True if this event is the tenant's genesis event written by platform.provision_tenant(). */
    public static boolean isGenesis(AuditEvent event) {
        return event.seq() == 1 && GENESIS_ACTION.equals(event.action())
                && event.actorType() == AuditActor.Type.PLATFORM;
    }

    /** Expected hash of an event given its predecessor's hash (handles genesis). */
    public static byte[] expectedHash(AuditEvent event, byte[] prevHash) {
        return isGenesis(event) ? genesisHash(event.tenantId(), event.occurredAt()) : hash(event, prevHash);
    }

    private static byte[] sha256(byte[] body, byte[] suffix) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(body);
            md.update(suffix);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void requireHashLength(byte[] h) {
        if (h == null || h.length != HASH_LENGTH) {
            throw new IllegalArgumentException("prev hash must be 32 bytes");
        }
    }
}
