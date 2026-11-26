package com.arrheniq.kernel.esign;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How many electronic signatures make a record effective (URS-MD-004; tenant settings {@code approval.<kind>}).
 * <ul>
 *   <li>{@code NONE}: submitting makes the record effective, without a signature</li>
 *   <li>{@code SINGLE}: one approver signs (meaning APPROVED)</li>
 *   <li>{@code DOUBLE}: a reviewer signs (REVIEWED), then a different approver signs (APPROVED)</li>
 * </ul>
 */
public enum ApprovalLevel {
    NONE(0), SINGLE(1), DOUBLE(2);

    private final int signatures;

    ApprovalLevel(int signatures) {
        this.signatures = signatures;
    }

    public int signaturesRequired() {
        return signatures;
    }

    /**
     * Parses a setting value as stored ({@code "DOUBLE"} as JSON text, quotes optional). A missing or
     * unreadable value means SINGLE: fail safe, never weaker than one signature.
     */
    public static ApprovalLevel parse(@Nullable String setting) {
        if (setting == null) {
            return SINGLE;
        }
        String v = setting.trim().replace("\"", "").toUpperCase(Locale.ROOT);
        for (ApprovalLevel l : values()) {
            if (l.name().equals(v)) {
                return l;
            }
        }
        return SINGLE;
    }
}
