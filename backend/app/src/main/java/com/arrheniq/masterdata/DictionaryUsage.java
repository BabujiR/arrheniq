package com.arrheniq.masterdata;

import java.util.List;
import java.util.Map;

/**
 * Where the values of each controlled list are used. Records refer to list values by <b>code</b> (text), not by a
 * foreign key, so "in use" means: some live record stores that code in one of these columns. A value in use can be
 * retired but never deleted (URS-MD-006), and its code never changes.
 * <p>
 * The SQL is built from these constants only (ADR 0019 rule 2). Add a column here when a new table stores a list
 * value; {@code DictionaryUsageTest} checks that every column named here exists.
 */
final class DictionaryUsage {

    /** A column holding list-value codes, optionally restricted (e.g. to one component type). */
    record Column(String table, String column, String condition) {
        Column(String table, String column) {
            this(table, column, "");
        }

        String selectCodes() {
            return "SELECT " + column + " AS code FROM " + table + " WHERE deleted_at IS NULL AND " + column
                    + " IS NOT NULL" + (condition.isEmpty() ? "" : " AND " + condition);
        }
    }

    private static List<Column> component(String... types) {
        return List.of(new Column("app.study_component", "component_code",
                "component_type IN ('" + String.join("','", types) + "')"));
    }

    static final Map<String, List<Column>> COLUMNS = Map.ofEntries(
            Map.entry("MEASUREMENT", List.of(new Column("app.test_method", "unit_code"),
                    new Column("app.spec_test", "unit_code"), new Column("app.protocol_version", "fill_unit_code"),
                    new Column("app.study", "fill_unit_code"))),
            Map.entry("DOSAGE_FORM", List.of(new Column("app.product", "dosage_form"))),
            Map.entry("STORAGE_CONDITION", List.of(new Column("app.study", "condition_code"),
                    new Column("app.protocol_version", "condition_code"))),
            Map.entry("STUDY_REASON", List.of(new Column("app.study", "reason_code"))),
            Map.entry("STUDY_LENGTH", List.of(new Column("app.study", "study_length_code"))),
            Map.entry("STORAGE_LOCATION", List.of(new Column("app.study", "storage_location_code"))),
            Map.entry("ANALYST", List.of(new Column("app.test_result", "analyst_code"))),
            Map.entry("PACKAGE_SIZE", component("PACKAGE_SIZE")),
            Map.entry("CONTAINER", component("CONTAINER")),
            Map.entry("CONTAINER_RESIN", component("CONTAINER_RESIN")),
            Map.entry("CLOSURE", component("CLOSURE")),
            Map.entry("LINER", component("LINER")),
            Map.entry("INNERSEAL", component("INNERSEAL")),
            Map.entry("DESICCANT", component("DESICCANT")),
            Map.entry("FILLER", component("FILLER")),
            Map.entry("BLISTER", component("BLISTER")),
            Map.entry("LIDDING_FOIL", component("LIDDING_FOIL")),
            Map.entry("FORMING_FOIL", component("FORMING_FOIL")),
            Map.entry("OTHER_COMPONENT", component("OTHER_1", "OTHER_2")),
            Map.entry("PRODUCTION_SITE", component("MANUFACTURING_SITE", "PACKAGING_SITE")),
            Map.entry("SUPPLIER", List.of(new Column("app.study_component", "manufacturer_code"))));

    private DictionaryUsage() { }

    /** One query returning every code of this list that is in use; empty for lists not referenced anywhere yet. */
    static String usedCodesQuery(String dictionaryCode) {
        List<Column> columns = COLUMNS.getOrDefault(dictionaryCode, List.of());
        if (columns.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        for (Column c : columns) {
            if (!sql.isEmpty()) {
                sql.append(" UNION ");
            }
            sql.append(c.selectCodes());
        }
        return sql.toString();
    }
}
