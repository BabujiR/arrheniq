package com.arrheniq.study;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.api.ApiException.FieldError;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Collects field errors; {@link #throwIfAny()} raises one 400 with all of them. */
final class StudyValidation {

    private final List<FieldError> errors = new ArrayList<>();

    static @Nullable String trimToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    void required(String field, @Nullable Object value) {
        if (value == null) {
            add(field, "REQUIRED", null);
        }
    }

    void maxLength(String field, @Nullable String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            add(field, "TOO_LONG", "at most " + maxLength + " characters");
        }
    }

    void add(String field, String code, @Nullable String message) {
        errors.add(new FieldError(field, code, message));
    }

    void throwIfAny() {
        if (!errors.isEmpty()) {
            throw ApiException.invalid(errors);
        }
    }
}
