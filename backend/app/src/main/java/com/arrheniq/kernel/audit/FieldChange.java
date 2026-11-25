package com.arrheniq.kernel.audit;

import org.jspecify.annotations.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One field-level change in an audit event: {@code {field, old, new}} (Part 11 §11.10(e):
 * previously recorded information is never obscured). Values must be canonical-JSON compatible.
 */
public record FieldChange(String field, @Nullable Object oldValue, @Nullable Object newValue) {

    public FieldChange {
        Objects.requireNonNull(field, "field");
    }

    /** JSON shape stored in audit.audit_event.changes and hashed. */
    public Map<String, @Nullable Object> toJsonMap() {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("old", oldValue);
        m.put("new", newValue);
        return m;
    }
}
