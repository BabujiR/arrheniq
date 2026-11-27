package com.arrheniq.study;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Study IDs from the tenant setting {@code study.code_pattern} (URS-STU-002, legacy EA_STUDY_ID_MASTER): {@code YYYY}
 * or {@code YY} is the year, a run of {@code n} the sequence number (zero-padded to that width, wider when it grows
 * past it), anything else is kept as written. Default {@code YYnnnn}: 260001, 260002, …
 */
final class StudyCodes {

    private static final Pattern TOKEN = Pattern.compile("YYYY|YY|n+");

    private StudyCodes() { }

    /** Checks a pattern: exactly one run of {@code n}, at most 20 characters. */
    static void validate(String pattern) {
        if (pattern.isBlank() || pattern.length() > 20) {
            throw new IllegalStateException("study.code_pattern must be 1-20 characters: '" + pattern + "'");
        }
        long runs = Pattern.compile("n+").matcher(pattern).results().count();
        if (runs != 1) {
            throw new IllegalStateException("study.code_pattern needs exactly one run of n: '" + pattern + "'");
        }
    }

    static String format(String pattern, int year, long number) {
        validate(pattern);
        Matcher m = TOKEN.matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group();
            String value = switch (token) {
                case "YYYY" -> String.format(Locale.ROOT, "%04d", year);
                case "YY" -> String.format(Locale.ROOT, "%02d", Math.floorMod(year, 100));
                default -> String.format(Locale.ROOT, "%0" + token.length() + "d", number);
            };
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The sequence is kept per year when the pattern contains the year, otherwise one sequence for ever. */
    static String scopeKey(String pattern, int year) {
        return pattern.contains("YY") ? String.valueOf(year) : "";
    }
}
