package com.arrheniq.kernel.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads and writes the {@code audit.audit_event.changes} JSON array ({@code [{field, old, new}]}). */
public final class ChangesCodec {

    private ChangesCodec() { }

    public static String toJson(List<FieldChange> changes) {
        List<Map<String, Object>> list = new ArrayList<>(changes.size());
        for (FieldChange c : changes) {
            list.add(c.toJsonMap());
        }
        return CanonicalJson.write(list);
    }

    public static List<FieldChange> parse(String json) {
        Object parsed = StrictJsonReader.read(json);
        if (!(parsed instanceof List<?> list)) {
            throw new IllegalArgumentException("changes must be a JSON array");
        }
        List<FieldChange> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("field") instanceof String field)) {
                throw new IllegalArgumentException("invalid change entry: " + o);
            }
            out.add(new FieldChange(field, m.get("old"), m.get("new")));
        }
        return out;
    }
}
