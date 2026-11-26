package com.arrheniq.masterdata;

import com.arrheniq.kernel.audit.CanonicalJson;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request body for a list value. {@code code} and {@code siteId} are fixed once created (records refer to the
 * code); updates change label, description, sort order and attributes.
 */
public record DictionaryValueInput(@Nullable String code, @Nullable String label, @Nullable String description,
                                   @Nullable Integer sortOrder, @Nullable UUID siteId,
                                   @Nullable Map<String, @Nullable Object> attributes) {

    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("[a-z][a-z0-9_]{0,39}");
    static final int MAX_ATTRIBUTES = 20;

    /** Validated and normalised. */
    record Clean(String code, String label, @Nullable String description, int sortOrder, @Nullable UUID siteId,
                 Map<String, Object> attributes) {

        /** Business fields for the audit trail (attributes as canonical JSON text). */
        Map<String, @Nullable Object> auditMap(String status) {
            Map<String, @Nullable Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", label);
            m.put("description", description);
            m.put("sortOrder", sortOrder);
            m.put("siteId", siteId);
            m.put("attributes", CanonicalJson.write(attributes));
            m.put("status", status);
            return m;
        }
    }

    Clean validate() {
        Validation v = new Validation();
        String c = Validation.normalizeCode(code);
        v.code("code", c);
        v.required("label", label, 200);
        v.maxLength("description", description, 1000);
        v.range("sortOrder", sortOrder, 0, 100_000);
        Map<String, Object> attrs = new TreeMap<>();
        if (attributes != null) {
            if (attributes.size() > MAX_ATTRIBUTES) {
                v.add("attributes", "TOO_MANY", "at most " + MAX_ATTRIBUTES + " attributes");
            }
            attributes.forEach((key, value) -> {
                if (!ATTRIBUTE_KEY.matcher(key).matches()) {
                    v.add("attributes." + key, "FORMAT", "lower-case letters, digits and _ (e.g. temp_c)");
                } else if (value != null) {   // null means "no such attribute": nothing to store
                    addAttribute(v, attrs, key, value);
                }
            });
        }
        v.throwIfAny();
        String trimmedLabel = Validation.trimToNull(label);
        return new Clean(c, trimmedLabel == null ? "" : trimmedLabel, Validation.trimToNull(description),
                sortOrder == null ? 0 : sortOrder, siteId, attrs);
    }

    /** Accepts text (at most 200 characters), whole numbers, exact decimals and true/false. */
    private static void addAttribute(Validation v, Map<String, Object> attrs, String key, Object value) {
        switch (value) {
            case String s when s.length() > 200 -> v.add("attributes." + key, "TOO_LONG", "at most 200 characters");
            case String s -> attrs.put(key, s);
            case Boolean b -> attrs.put(key, b);
            case Integer i -> attrs.put(key, i);
            case Long l -> attrs.put(key, l);
            case Double d -> attrs.put(key, new BigDecimal(d.toString()));   // exact decimal, as written
            case Float f -> attrs.put(key, new BigDecimal(f.toString()));
            case BigDecimal bd -> attrs.put(key, bd);
            default -> v.add("attributes." + key, "INVALID_VALUE", "text, number or true/false");
        }
    }
}
