package com.arrheniq.kernel.security;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What the user is about to sign, fixed <em>before</em> they re-enter their password (ADR 0019, amended in S1.1;
 * Part 11 §11.70, §11.200). The re-authentication evidence carries it, and {@code SignatureService} refuses to sign
 * any other record or any other version of it. So a password entered to approve product A at version 3 can never
 * approve product B, nor product A after someone changed it.
 *
 * @param id            stored on the signature ({@code e_signature.signing_intent_id}) to link it to the re-authentication
 * @param recordType    e.g. PRODUCT, SPECIFICATION
 * @param recordId      the record
 * @param recordVersion the version the user reviewed (the record's optimistic-lock version)
 */
public record SigningIntent(UUID id, String recordType, UUID recordId, long recordVersion) {

    private static final Pattern RECORD_TYPE = Pattern.compile("[A-Z][A-Z_]{1,39}");

    public SigningIntent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(recordType, "recordType");
        Objects.requireNonNull(recordId, "recordId");
        if (!RECORD_TYPE.matcher(recordType).matches()) {
            throw new IllegalArgumentException("invalid record type");
        }
        if (recordVersion < 0) {
            throw new IllegalArgumentException("invalid record version");
        }
    }

    /** True if this intent is for exactly this record at exactly this version. */
    public boolean covers(String type, UUID record, long version) {
        return recordType.equals(type) && recordId.equals(record) && recordVersion == version;
    }
}
