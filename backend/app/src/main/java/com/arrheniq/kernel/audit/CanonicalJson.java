package com.arrheniq.kernel.audit;

import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "ArrheniQ canonical JSON v1": the deterministic serialisation hashed into the audit chain
 * (docs/architecture/audit-esignature.md §3; ADR 0004). Rules:
 * <ul>
 *   <li>objects: keys sorted by {@link String#compareTo} (UTF-16 order), no whitespace</li>
 *   <li>strings: RFC 8259 escaping of {@code "} {@code \} and control characters; other characters as-is (UTF-8)</li>
 *   <li>numbers: integers and {@link BigDecimal} only, written as {@code stripTrailingZeros().toPlainString()};
 *       floating point is rejected because it isn't exact</li>
 *   <li>{@link Instant}/{@link OffsetDateTime}: UTC, microsecond precision {@code yyyy-MM-ddTHH:mm:ss.SSSSSSZ}
 *       (PostgreSQL timestamptz precision); {@link LocalDate}: ISO date; {@link UUID}: lower-case; enums: name()</li>
 * </ul>
 * Changing these rules changes every hash: create "v2" instead and record the version.
 */
public final class CanonicalJson {

    public static final String VERSION = "v1";

    static final DateTimeFormatter MICROS_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    private CanonicalJson() { }

    public static String write(@Nullable Object value) {
        StringBuilder out = new StringBuilder(256);
        append(out, value);
        return out.toString();
    }

    /** Formats an instant exactly as stored and hashed (microseconds, UTC). */
    public static String formatInstant(Instant instant) {
        return MICROS_UTC.format(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private static void append(StringBuilder out, @Nullable Object value) {
        switch (value) {
            case null -> out.append("null");
            case String s -> appendString(out, s);
            case Boolean b -> out.append(b.booleanValue() ? "true" : "false");
            case Integer i -> out.append(i.intValue());
            case Long l -> out.append(l.longValue());
            case Short sh -> out.append(sh.shortValue());
            case BigInteger bi -> out.append(bi.toString());
            case BigDecimal bd -> out.append(bd.signum() == 0 ? "0" : bd.stripTrailingZeros().toPlainString());
            case Double d -> throw new IllegalArgumentException("floating point not allowed in canonical JSON: " + d);
            case Float f -> throw new IllegalArgumentException("floating point not allowed in canonical JSON: " + f);
            case UUID u -> appendString(out, u.toString());
            case Instant i -> appendString(out, formatInstant(i));
            case OffsetDateTime o -> appendString(out, formatInstant(o.toInstant()));
            case LocalDate d -> appendString(out, d.toString());
            case Enum<?> e -> appendString(out, e.name());
            case Map<?, ?> m -> appendObject(out, m);
            case List<?> list -> appendArray(out, list);
            default -> throw new IllegalArgumentException(
                    "unsupported type in canonical JSON: " + value.getClass().getName());
        }
    }

    private static void appendObject(StringBuilder out, Map<?, ?> map) {
        List<String> keys = new ArrayList<>(map.size());
        for (Object k : map.keySet()) {
            if (!(k instanceof String key)) {
                throw new IllegalArgumentException("canonical JSON object keys must be strings: " + k);
            }
            keys.add(key);
        }
        keys.sort(String::compareTo);
        out.append('{');
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendString(out, key);
            out.append(':');
            append(out, map.get(key));
        }
        out.append('}');
    }

    private static void appendArray(StringBuilder out, List<?> list) {
        out.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            append(out, list.get(i));
        }
        out.append(']');
    }

    private static void appendString(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
