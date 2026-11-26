-- =============================================================================
-- V14: protocol builder (S1.5; URS-PRO-001/002/003/005/007)
-- 1) A protocol version names the storage condition it is meant for (dictionary STORAGE_CONDITION), a change
--    summary, and the date it became effective (approval).
-- 2) At most one APPROVED (effective) version per protocol.
-- 3) A draft's time points, tests and test x time-point cells are rewritten on every save; rows are only ever
--    logically deleted (GxP, URS-AUD-005), so uniqueness applies to live rows only.
-- =============================================================================
ALTER TABLE app.protocol_version
    ADD COLUMN condition_code text,
    ADD COLUMN change_summary text,
    ADD COLUMN effective_from date;
COMMENT ON COLUMN app.protocol_version.condition_code IS 'Intended storage condition (dictionary STORAGE_CONDITION code).';

CREATE UNIQUE INDEX protocol_version_one_approved ON app.protocol_version (tenant_id, protocol_id)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

DO $$
DECLARE
    c record;
BEGIN
    FOR c IN SELECT conrelid::regclass AS tbl, conname
               FROM pg_constraint
              WHERE contype = 'u'
                AND conrelid IN ('app.protocol_timepoint'::regclass, 'app.protocol_test'::regclass,
                                 'app.protocol_matrix_cell'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;
END
$$;

CREATE UNIQUE INDEX protocol_timepoint_live_uq ON app.protocol_timepoint (tenant_id, protocol_version_id, seq)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX protocol_test_live_uq ON app.protocol_test (tenant_id, protocol_version_id, test_method_id)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX protocol_matrix_cell_live_uq ON app.protocol_matrix_cell
    (tenant_id, protocol_test_id, protocol_timepoint_id) WHERE deleted_at IS NULL;
