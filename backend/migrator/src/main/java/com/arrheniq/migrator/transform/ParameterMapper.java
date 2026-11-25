package com.arrheniq.migrator.transform;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps legacy EA_APPL_PARAMETER rows to ArrheniQ tenant settings (legacy-mapping.md §6).
 * Secrets are never migrated (security-findings S-07).
 */
public final class ParameterMapper {

    /** A target setting: key + JSON literal value. */
    public record Setting(String key, String jsonValue) { }

    public enum Outcome { MAPPED, NOT_MIGRATED, SECRET_DISCARDED, UNKNOWN }

    public record Mapping(Outcome outcome, Setting setting, String note) { }

    private static final Set<String> SECRETS = Set.of("EMAIL ADMIN PASSWORD", "DATA_LOCK_PASSWORD");

    private static final Map<String, String> NOT_MIGRATED = Map.of(
            "INVALID LOGIN COUNT", "IdP policy (report to admin)",
            "PASSWORD VALID PERIOD", "IdP policy (report to admin)",
            "PASSWORD REQUIRED FOR EDIT", "superseded by e-signature points",
            "SCHEDULE APPROVAL LEVEL", "not used by legacy code",
            "EMAIL HOST", "platform email service",
            "EMAIL PORT", "platform email service",
            "EMAIL ADMIN USER ID", "platform email service",
            "DATA_LOCK", "replaced by result locking + re-approval");

    public Mapping map(String name, String value) {
        String p = name == null ? "" : name.strip().toUpperCase(Locale.ROOT);
        String v = value == null ? "" : value.strip();
        if (SECRETS.contains(p)) {
            return new Mapping(Outcome.SECRET_DISCARDED, null, "secret value discarded");
        }
        if (NOT_MIGRATED.containsKey(p)) {
            return new Mapping(Outcome.NOT_MIGRATED, null, NOT_MIGRATED.get(p));
        }
        return switch (p) {
            case "SYSTEM LOCK OUT" -> number("session.idle_lock_minutes", v);
            case "SYSTEM LOG OFF" -> number("session.logoff_minutes", v);
            case "DATA LOCK OUT" -> number("session.idle_lock_minutes", v);
            case "DAYS TO SAMPLE PULL" -> number("notify.days_before_pull", v);
            case "DAYS TO SAMPLE TEST CYCLE" -> number("notify.days_after_pull_not_started", v);
            case "DAYS TO COMPLETE SAMPLE TEST" -> number("notify.days_after_pull_not_completed", v);
            case "SPECIFICATION APPROVAL LEVEL" -> approval("approval.specification", v);
            case "NEW STUDY APPROVAL LEVEL" -> approval("approval.study", v);
            case "DATA APPROVAL LEVEL" -> approval("approval.results", v);
            case "NOTIFY_SPEC_APPROVAL_REQUEST", "NOTIFY_STUDY_APPROVAL_REQUEST", "NOTIFY_DATA_APPROVAL_REQUEST" ->
                    yesNo("approval.notify_on_request", v);
            case "MOVE SATURDAY PULL DATE" -> weekend("scheduling.saturday_rule", v);
            case "MOVE SUNDAY PULL DATE" -> weekend("scheduling.sunday_rule", v);
            case "INVENTORY PULL DAYS BUFFER" -> number("scheduling.pull_window_days", v);
            case "INVENTORY EXTRAS ALLOWED" -> yesNo("inventory.extras_allowed", v);
            case "PARTIAL INVENTORY PULL ALLOWED" -> yesNo("inventory.partial_pull_allowed", v);
            case "LABEL_REQUIRED_FOR_STUDY" -> yesNo("labels.required_for_study_approval", v);
            case "DATA_ENTRY_REF_INFO_REQD" -> yesNo("results.reference_required", v);
            case "DEFAULT LATE DAYS OK LEVEL" -> number("reports.late_days_ok", v);
            case "COMPANY NAME" -> new Mapping(Outcome.MAPPED, new Setting("company.name", jsonString(v)), null);
            default -> new Mapping(Outcome.UNKNOWN, null, "unknown legacy parameter: " + name);
        };
    }

    private static Mapping number(String key, String v) {
        return parseNonNegativeInt(v)
                .map(n -> new Mapping(Outcome.MAPPED, new Setting(key, Integer.toString(n)), null))
                .orElseGet(() -> new Mapping(Outcome.UNKNOWN, null, "non-numeric value '" + v + "' for " + key));
    }

    private static Mapping yesNo(String key, String v) {
        return switch (v.toUpperCase(Locale.ROOT)) {
            case "Y", "YES" -> new Mapping(Outcome.MAPPED, new Setting(key, "true"), null);
            case "N", "NO" -> new Mapping(Outcome.MAPPED, new Setting(key, "false"), null);
            default -> new Mapping(Outcome.UNKNOWN, null, "expected Y/N for " + key + " but got '" + v + "'");
        };
    }

    private static Mapping approval(String key, String v) {
        String level = switch (v.toUpperCase(Locale.ROOT)) {
            case "N" -> "NONE";
            case "S" -> "SINGLE";
            case "D" -> "DOUBLE";
            default -> null;
        };
        return level == null
                ? new Mapping(Outcome.UNKNOWN, null, "expected N/S/D for " + key + " but got '" + v + "'")
                : new Mapping(Outcome.MAPPED, new Setting(key, jsonString(level)), null);
    }

    private static Mapping weekend(String key, String v) {
        String rule = switch (v.toUpperCase(Locale.ROOT)) {
            case "L" -> "LEAVE";
            case "F" -> "MOVE_BEFORE";
            case "M" -> "MOVE_AFTER";
            default -> null;
        };
        return rule == null
                ? new Mapping(Outcome.UNKNOWN, null, "expected L/F/M for " + key + " but got '" + v + "'")
                : new Mapping(Outcome.MAPPED, new Setting(key, jsonString(rule)), null);
    }

    private static Optional<Integer> parseNonNegativeInt(String v) {
        try {
            int n = Integer.parseInt(v);
            return n >= 0 ? Optional.of(n) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
