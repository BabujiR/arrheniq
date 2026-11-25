package com.arrheniq.migrator.reconcile;

/**
 * One exception found during migration (migration-design.md §5).
 * BLOCKER issues abort a PRODUCTION run before loading.
 */
public record MigrationIssue(String code, Severity severity, String legacyTable, String legacyKey, String message) {

    public enum Severity { BLOCKER, WARNING, INFO }

    public static MigrationIssue blocker(String code, String table, String key, String message) {
        return new MigrationIssue(code, Severity.BLOCKER, table, key, message);
    }

    public static MigrationIssue warning(String code, String table, String key, String message) {
        return new MigrationIssue(code, Severity.WARNING, table, key, message);
    }

    public static MigrationIssue info(String code, String table, String key, String message) {
        return new MigrationIssue(code, Severity.INFO, table, key, message);
    }
}
