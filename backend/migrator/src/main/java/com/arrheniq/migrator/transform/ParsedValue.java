package com.arrheniq.migrator.transform;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Structured representation of one legacy result replicate (EA_PRD_STUDY_TEST_RESULTS.RESULT_n).
 * Exactly one of {numericValue, qualifier-only, textValue} carries the meaning; rawInput is always kept.
 * See docs/data/legacy-mapping.md §7.
 */
public record ParsedValue(BigDecimal numericValue, Qualifier qualifier, String textValue, String rawInput, boolean parsed) {

    /** Qualifiers aligned with app.result_replicate.qualifier CHECK constraint. */
    public enum Qualifier { LT, GT, ND, LOQ, LOD, NT, NA }

    public ParsedValue {
        Objects.requireNonNull(rawInput, "rawInput");
    }

    public static ParsedValue numeric(BigDecimal value, String raw) {
        return new ParsedValue(value, null, null, raw, true);
    }

    public static ParsedValue qualified(BigDecimal value, Qualifier q, String raw) {
        return new ParsedValue(value, q, null, raw, true);
    }

    public static ParsedValue text(String text, String raw, boolean parsed) {
        return new ParsedValue(null, null, text, raw, parsed);
    }
}
