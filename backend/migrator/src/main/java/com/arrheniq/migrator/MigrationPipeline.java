package com.arrheniq.migrator;

import com.arrheniq.migrator.extract.LegacySource;
import com.arrheniq.migrator.extract.LegacyTables;
import com.arrheniq.migrator.load.TargetWriter;
import com.arrheniq.migrator.reconcile.MigrationIssue;
import com.arrheniq.migrator.reconcile.ReconciliationReport;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates the eight stages of migration-design.md §2.
 * Skeleton: stages 3–6 are wired as extension points and implemented in Phase 6 (E16).
 */
public final class MigrationPipeline {

    public enum Mode { DRY_RUN, PRODUCTION }

    public static final String MAPPING_VERSION = "1.0.0";

    private final LegacySource source;
    private final TargetWriter target;
    private final Mode mode;
    private final UUID tenantId;

    public MigrationPipeline(LegacySource source, TargetWriter target, Mode mode, UUID tenantId) {
        this.source = source;
        this.target = target;
        this.mode = mode;
        this.tenantId = tenantId;
    }

    public ReconciliationReport run() throws Exception {
        UUID runId = UUID.randomUUID();
        ReconciliationReport report = new ReconciliationReport(runId.toString(), MAPPING_VERSION, Instant.now());

        // Stage 1–2: extract + profile (counts every table, including excluded ones)
        for (String table : LegacyTables.MIGRATED) {
            report.extracted(table, source.count(table));
        }
        for (String table : LegacyTables.EXCLUDED) {
            long n = source.count(table);
            report.extracted(table, n);
            report.excluded(table, n);
        }

        // Stage 3–4: transform + validate (implemented per aggregate in Phase 6)
        transformAndValidate(report);

        if (mode == Mode.PRODUCTION && report.hasBlockers()) {
            report.issue(MigrationIssue.blocker("MIG-E000", null, null,
                    "PRODUCTION run aborted before load: blockers present"));
            return report;
        }

        // Stage 5–6: load + recompute
        load(report, runId);

        // Stage 7: reconcile
        for (String unbalanced : report.unbalancedTables()) {
            report.issue(MigrationIssue.blocker("MIG-E099", unbalanced, null,
                    "source rows not fully accounted for (mapped + excluded + rejected != extracted)"));
        }
        return report;
    }

    /** Extension point: per-aggregate transformers (Phase 6). */
    void transformAndValidate(ReconciliationReport report) throws Exception {
        // Implemented in Phase 6: products/specs, protocols, studies, time points, inventory, results, audit history.
    }

    /** Extension point: batch loading through TargetWriter (Phase 6). */
    void load(ReconciliationReport report, UUID runId) throws Exception {
        target.beginBatch(tenantId, runId);
        target.commitBatch();
    }
}
