package com.arrheniq.kernel.audit;

import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes field-level differences between two business snapshots of a record
 * (audit-esignature.md §4). Technical fields are excluded; numeric values are compared by value
 * (1.50 equals 1.5) so a re-save without a real change produces no audit noise.
 */
public final class FieldDiff {

    /** Columns that are bookkeeping, not business content. */
    public static final Set<String> TECHNICAL_FIELDS = Set.of(
            "tenantId", "id", "version", "createdAt", "createdBy", "updatedAt", "updatedBy", "contentHash");

    private FieldDiff() { }

    /**
     * @param before snapshot before the change; {@code null} for a newly created record
     * @param after  snapshot after the change; {@code null} for a removed record
     * @return changes sorted by field name; empty if nothing changed
     */
    public static List<FieldChange> between(@Nullable Map<String, ?> before, @Nullable Map<String, ?> after) {
        Set<String> fields = new TreeSet<>();
        if (before != null) {
            fields.addAll(before.keySet());
        }
        if (after != null) {
            fields.addAll(after.keySet());
        }
        List<FieldChange> changes = new ArrayList<>();
        for (String field : fields) {
            if (TECHNICAL_FIELDS.contains(field)) {
                continue;
            }
            Object oldValue = before == null ? null : before.get(field);
            Object newValue = after == null ? null : after.get(field);
            if (!sameValue(oldValue, newValue)) {
                changes.add(new FieldChange(field, oldValue, newValue));
            }
        }
        return changes;
    }

    static boolean sameValue(@Nullable Object a, @Nullable Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
}
