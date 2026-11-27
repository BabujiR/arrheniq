-- =============================================================================
-- V15: study login and schedule generation (S2.2; URS-STU-001/002/004/005, URS-SCH-001/002)
-- 1) A study records the pull-date algorithm (and version) its schedule was generated with (ADR 0011).
-- 2) No approved study without a schedule (URS-STU-005): checked at commit by a deferred constraint trigger, so
--    the study and its time points are written in one transaction and a half-done approval can't be committed.
-- 3) Live study time points are unique per study and sequence (rows are only ever logically deleted).
-- =============================================================================
ALTER TABLE app.study
    ADD COLUMN schedule_algorithm text;
COMMENT ON COLUMN app.study.schedule_algorithm IS
    'Pull-date algorithm id@version used to generate the schedule, e.g. schedule.pull-dates@1.0.0 (ADR 0011).';

CREATE FUNCTION app.study_requires_schedule() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.status IN ('APPROVED', 'COMPLETED') AND NEW.deleted_at IS NULL AND NEW.origin = 'NATIVE'
       AND NOT EXISTS (SELECT 1 FROM app.study_timepoint t
                        WHERE t.tenant_id = NEW.tenant_id AND t.study_id = NEW.id AND t.deleted_at IS NULL) THEN
        RAISE EXCEPTION 'ARQ-STU-001: approved study % has no schedule', NEW.study_code
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER study_approved_has_schedule
    AFTER INSERT OR UPDATE OF status ON app.study
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION app.study_requires_schedule();

ALTER TABLE app.study_timepoint DROP CONSTRAINT IF EXISTS study_timepoint_tenant_id_study_id_seq_key;
CREATE UNIQUE INDEX study_timepoint_live_uq ON app.study_timepoint (tenant_id, study_id, seq)
    WHERE deleted_at IS NULL;
CREATE INDEX study_group_idx ON app.study (tenant_id, study_group_id);
