package com.arrheniq.migrator.extract;

import java.util.List;

/**
 * Whitelist of legacy eSTAR tables the migrator reads, in extraction order.
 * Table names are never taken from user input (security-findings S-03/S-06).
 */
public final class LegacyTables {

    private LegacyTables() { }

    /** Tables that are extracted and mapped (legacy-mapping.md §1–5). */
    public static final List<String> MIGRATED = List.of(
            "EA_LOCATIONS",
            "EA_APP_USER",
            "EA_APP_ACCESS_TEMPLATE",
            "EA_APP_TEMPLATE_MODULE",
            "EA_APP_USER_MODULE_ACCESS",
            "EA_APP_USER_LOCATIONS",
            "EA_APP_MODULE",              // read for module → permission mapping only
            "EA_APPL_PARAMETER",
            "EA_DICTIONARY_MASTER",
            "EA_DICTIONARY_DETAIL",
            "EA_STUDY_ID_MASTER",
            "EA_PRD_TESTS",
            "EA_PRD_PRODUCTS",
            "EA_PRD_PRODUCT_TESTS",
            "EA_PRD_PRODUCT_SPECS",
            "EA_PRD_PRODUCT_FORMULATION",
            "EA_PRD_PRODUCT_BATCHES",
            "EA_PRD_PROTOCOLS",
            "EA_PRD_PROTOCOL_DETAILS",
            "EA_PRD_INVENTORY",
            "EA_PRD_PRODUCT_STUDY",
            "EA_PRD_STUDY_CONDITIONS",
            "EA_PRD_STUDY_RAW_MATERIALS",
            "EA_PRD_STUDY_ATTACHMENTS",
            "EA_PRD_STUDY_INTERVALS",
            "EA_PRD_STUDY_SCHEDULES",
            "EA_STUDY_INV_DESTROYED",
            "EA_PRD_STUDY_TEST_RESULTS",
            "EA_PRD_LABELS",
            "EA_LABEL_PRINT_REQUESTS",
            "EA_LABEL_PRINT_REQUEST_DETAILS",
            "EA_APL_EDIT_REASONS",
            "EA_APP_USER_AUDIT",
            "EA_MODULE_ACCESS_AUDIT",
            "EA_REPORT_COLUMN",
            "EA_RPT_USER_PREFERENCE");

    /** Tables intentionally not migrated (legacy-mapping.md §10); counted in reconciliation as EXCLUDED. */
    public static final List<String> EXCLUDED = List.of(
            "EA_APP_AREA",
            "EA_DEFAULT_APPL_PARAMETER",
            "EA_DEFAULT_DICTIONARY_DETAIL",
            "EA_PRODUCT_REGISTRATION",
            "EA_REGR_TINV",
            "EA_PRD_IMAGES",
            "EA_PHARMA_NEW_SAMPLE");

    /** Columns never extracted (secrets / credentials). */
    public static final List<String> NEVER_EXTRACT_COLUMNS = List.of(
            "PASSWORD", "PREV_PASSWORD1", "PREV_PASSWORD2", "PREV_PASSWORD3",
            "LICENSE_KEY", "LICENSE_STRING");

    public static boolean isKnown(String table) {
        return MIGRATED.contains(table) || EXCLUDED.contains(table);
    }
}
