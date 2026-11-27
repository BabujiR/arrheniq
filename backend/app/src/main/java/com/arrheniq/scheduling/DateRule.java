package com.arrheniq.scheduling;

import java.util.Locale;

/**
 * What happens to a pull date that falls on a Saturday, a Sunday or a site holiday (URS-SCH-002, BR-027).
 * Legacy {@code MOVE SATURDAY/SUNDAY PULL DATE}: L = {@link #LEAVE}, F = {@link #MOVE_BEFORE}, M = {@link #MOVE_AFTER}.
 */
public enum DateRule {
    /** Keep the date. */
    LEAVE,
    /** The previous working day (Monday to Friday, not a site holiday). */
    MOVE_BEFORE,
    /** The next working day. */
    MOVE_AFTER;

    /** Reads a stored setting value; anything else is a configuration error, never a silent default (ADR 0011). */
    public static DateRule parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown pull-date rule '" + value + "' (LEAVE, MOVE_BEFORE, MOVE_AFTER)", e);
        }
    }
}
