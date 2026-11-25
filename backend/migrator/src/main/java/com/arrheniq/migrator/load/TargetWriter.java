package com.arrheniq.migrator.load;

import java.util.UUID;

/**
 * Loads transformed aggregates into ArrheniQ through the application's domain services
 * (migration-design.md principle 2), never with raw SQL. Implemented in Phase 6 once the
 * app modules exist; every call runs inside an audit context with actor_type = MIGRATION
 * and writes app.legacy_id_map entries.
 */
public interface TargetWriter extends AutoCloseable {

    /** Opens a batch transaction for the tenant (sets tenant + audit context). */
    void beginBatch(UUID tenantId, UUID migrationRunId);

    /** Records that a legacy row produced a target row (URS-MIG-003). */
    void mapLegacyId(String legacyTable, String legacyKey, String targetTable, UUID targetId);

    void commitBatch();

    void rollbackBatch();

    @Override
    void close();
}
