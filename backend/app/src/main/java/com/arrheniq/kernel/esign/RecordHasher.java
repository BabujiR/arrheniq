package com.arrheniq.kernel.esign;

import com.arrheniq.kernel.audit.CanonicalJson;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Content hash a signature is bound to (ADR 0016; Part 11 §11.70): SHA-256 of the canonical JSON
 * (audit canonical rules v1) of the record's <b>business content</b>. Workflow fields (status,
 * version, timestamps) are not part of it, so approving a record doesn't change what was signed,
 * while any business change does.
 */
public final class RecordHasher {

    private RecordHasher() { }

    public static byte[] contentHash(Map<String, ? extends @Nullable Object> businessContent) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(CanonicalJson.write(businessContent).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
