package com.arrheniq.migrator.transform;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts legacy free-text EA_PRD_PRODUCTS.EXPIRATION_PERIOD (e.g. "24 Months", "2 years",
 * "To be determined") into structured values for app.product (legacy-mapping.md §3; BR-007).
 */
public final class ExpirationPeriodParser {

    public enum Status { ESTABLISHED, TO_BE_DETERMINED, NOT_APPLICABLE, UNPARSEABLE }

    /** value/unit are null unless status == ESTABLISHED. unit is always MONTH, WEEK or DAY. */
    public record Result(Status status, Integer value, String unit, String raw) { }

    private static final Pattern PERIOD = Pattern.compile(
            "^(\\d{1,3})\\s*(months?|mos?\\.?|m|years?|yrs?\\.?|y|weeks?|wks?\\.?|w|days?|d)\\b.*$");

    public Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Result(Status.TO_BE_DETERMINED, null, null, raw);
        }
        String v = raw.strip().toLowerCase(Locale.ROOT);
        if (v.startsWith("to be determined") || v.startsWith("not established") || v.equals("tbd")) {
            return new Result(Status.TO_BE_DETERMINED, null, null, raw);
        }
        if (v.startsWith("not applicable") || v.equals("n/a") || v.equals("na")) {
            return new Result(Status.NOT_APPLICABLE, null, null, raw);
        }
        Matcher m = PERIOD.matcher(v);
        if (!m.matches()) {
            return new Result(Status.UNPARSEABLE, null, null, raw);
        }
        int n = Integer.parseInt(m.group(1));
        if (n <= 0) {
            return new Result(Status.UNPARSEABLE, null, null, raw);
        }
        char u = m.group(2).charAt(0);
        return switch (u) {
            case 'm' -> new Result(Status.ESTABLISHED, n, "MONTH", raw);
            case 'y' -> new Result(Status.ESTABLISHED, n * 12, "MONTH", raw);
            case 'w' -> new Result(Status.ESTABLISHED, n, "WEEK", raw);
            case 'd' -> new Result(Status.ESTABLISHED, n, "DAY", raw);
            default -> new Result(Status.UNPARSEABLE, null, null, raw);
        };
    }
}
