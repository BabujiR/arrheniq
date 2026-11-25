package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.api.ApiException.FieldError;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Small collector for field errors; {@link #throwIfAny()} raises one 400 with all of them. */
final class Validation {

    static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9._/-]{0,39}$");

    private final List<FieldError> errors = new ArrayList<>();

    static String normalizeCode(@Nullable String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    static @Nullable String trimToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    void code(String field, String value) {
        if (!CODE.matcher(value).matches()) {
            add(field, "FORMAT", "1-40 characters: A-Z, 0-9, . _ / -, starting with a letter or digit");
        }
    }

    void required(String field, @Nullable String value, int maxLength) {
        if (value == null || value.isBlank()) {
            add(field, "REQUIRED", null);
        } else {
            maxLength(field, value, maxLength);
        }
    }

    void maxLength(String field, @Nullable String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            add(field, "TOO_LONG", "at most " + maxLength + " characters");
        }
    }

    void oneOf(String field, @Nullable String value, Set<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            add(field, "INVALID_VALUE", "one of " + new java.util.TreeSet<>(allowed));
        }
    }

    void range(String field, @Nullable Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            add(field, "OUT_OF_RANGE", min + ".." + max);
        }
    }

    void absent(String field, @Nullable Object value, String why) {
        if (value != null) {
            add(field, "NOT_ALLOWED", why);
        }
    }

    void ordered(String lowerField, @Nullable BigDecimal lower, @Nullable BigDecimal upper) {
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            add(lowerField, "ORDER", "lower limit must not be greater than the upper limit");
        }
    }

    void notNegative(String field, @Nullable BigDecimal value) {
        if (value != null && value.signum() < 0) {
            add(field, "NEGATIVE", "must be 0 or greater");
        }
    }

    void add(String field, String code, @Nullable String message) {
        errors.add(new FieldError(field, code, message));
    }

    List<FieldError> errors() {
        return List.copyOf(errors);
    }

    void throwIfAny() {
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }
}
