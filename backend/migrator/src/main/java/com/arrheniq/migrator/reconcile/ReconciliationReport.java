package com.arrheniq.migrator.reconcile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reconciliation evidence for one migration run (URS-MIG-002). Every source row must be
 * accounted for: mapped + excluded + rejected == extracted, per legacy table.
 */
public final class ReconciliationReport {

    /** Per legacy table counters. */
    public static final class TableCount {
        long extracted;
        long mapped;
        long excluded;
        long rejected;

        public long extracted() { return extracted; }
        public long mapped() { return mapped; }
        public long excluded() { return excluded; }
        public long rejected() { return rejected; }

        public boolean balanced() {
            return extracted == mapped + excluded + rejected;
        }
    }

    private final String runId;
    private final String mappingVersion;
    private final Instant startedAt;
    private final Map<String, TableCount> tables = new TreeMap<>();
    private final Map<String, Long> targetRows = new TreeMap<>();
    private final List<MigrationIssue> issues = new ArrayList<>();

    public ReconciliationReport(String runId, String mappingVersion, Instant startedAt) {
        this.runId = runId;
        this.mappingVersion = mappingVersion;
        this.startedAt = startedAt;
    }

    public void extracted(String table, long n) { counts(table).extracted += n; }
    public void mapped(String table, long n) { counts(table).mapped += n; }
    public void excluded(String table, long n) { counts(table).excluded += n; }
    public void rejected(String table, long n) { counts(table).rejected += n; }
    public void loaded(String targetTable, long n) { targetRows.merge(targetTable, n, Long::sum); }
    public void issue(MigrationIssue issue) { issues.add(issue); }

    private TableCount counts(String table) {
        return tables.computeIfAbsent(table, t -> new TableCount());
    }

    public boolean hasBlockers() {
        return issues.stream().anyMatch(i -> i.severity() == MigrationIssue.Severity.BLOCKER);
    }

    /** Tables whose source rows are not fully accounted for. Must be empty for a valid run. */
    public List<String> unbalancedTables() {
        return tables.entrySet().stream().filter(e -> !e.getValue().balanced()).map(Map.Entry::getKey).toList();
    }

    public String runId() { return runId; }
    public String mappingVersion() { return mappingVersion; }
    public Instant startedAt() { return startedAt; }
    public Map<String, TableCount> tables() { return Collections.unmodifiableMap(tables); }
    public Map<String, Long> targetRows() { return Collections.unmodifiableMap(targetRows); }
    public List<MigrationIssue> issues() { return Collections.unmodifiableList(issues); }
}
