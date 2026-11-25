-- =============================================================================
-- V6: schedule (time points), inventory ledger, results, investigations
-- URS-SCH-*, INV-*, RES-*, REV-*, OOT-*; legacy EA_PRD_STUDY_INTERVALS,
-- EA_PRD_STUDY_SCHEDULES, EA_STUDY_INV_DESTROYED, EA_PRD_STUDY_TEST_RESULTS
-- =============================================================================

CREATE TABLE app.study_timepoint (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    study_id             uuid NOT NULL,
    protocol_timepoint_id uuid,                      -- NULL for user-defined time points
    seq                  int  NOT NULL CHECK (seq >= 0),             -- legacy INTERVAL_NUMBER
    value                numeric NOT NULL CHECK (value >= 0),        -- legacy INTERVAL_VALUE
    unit                 text NOT NULL CHECK (unit IN ('DAY','WEEK','MONTH')),
    label                text,
    calculated_date      date NOT NULL,              -- pure calendar result (BR-025/026)
    scheduled_date       date NOT NULL,              -- after weekend/holiday rule or manual change (BR-027)
    date_rule_applied    text,                       -- e.g. SATURDAY_TO_FRIDAY, HOLIDAY_TO_NEXT_WORKDAY, MANUAL
    window_days_before   int NOT NULL DEFAULT 0 CHECK (window_days_before >= 0),
    window_days_after    int NOT NULL DEFAULT 0 CHECK (window_days_after >= 0),
    units_planned        int NOT NULL DEFAULT 0 CHECK (units_planned >= 0),
    status               text NOT NULL DEFAULT 'SCHEDULED'
                         CHECK (status IN ('SCHEDULED','PULLED','IN_TESTING','COMPLETED','TERMINATED')),
    pulled_at            timestamptz,
    pulled_on            date,                       -- legacy DATE_PULLED (site calendar date)
    pulled_by            uuid,
    testing_started_on   date,                       -- legacy TEST_START_DATE
    testing_completed_on date,                       -- legacy TEST_END_DATE
    completed_at         timestamptz,
    back_entered         boolean NOT NULL DEFAULT false,   -- legacy PAST_INTERVAL = 'Y' (BR-030)
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    FOREIGN KEY (tenant_id, protocol_timepoint_id) REFERENCES app.protocol_timepoint (tenant_id, id),
    UNIQUE (tenant_id, study_id, seq)
);
CREATE INDEX study_timepoint_due_idx ON app.study_timepoint (tenant_id, scheduled_date) WHERE status = 'SCHEDULED';
COMMENT ON TABLE app.study_timepoint IS 'Legacy EA_PRD_STUDY_INTERVALS + EA_PRD_STUDY_SCHEDULES (1:1 in practice). URS-SCH-001..007.';

CREATE TABLE app.inventory_entry (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    study_id             uuid NOT NULL,
    study_timepoint_id   uuid,
    entry_type           text NOT NULL CHECK (entry_type IN
                           ('PLACE','PULL','EXTRA_PULL','REVERSAL','DESTROY','TRANSFER_TO_RESERVE','ADJUST')),
    quantity             int  NOT NULL,              -- positive = into stock, negative = out of stock
    reverses_entry_id    uuid,
    performed_on         date NOT NULL,
    performed_by         uuid NOT NULL,
    witness_user_id      uuid,                       -- destruction witness (URS-INV-006)
    method               text,                       -- destruction method
    scanned_label        text,                       -- barcode value confirmed at pull (URS-INV-002)
    comment              text,
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    FOREIGN KEY (tenant_id, study_timepoint_id) REFERENCES app.study_timepoint (tenant_id, id),
    FOREIGN KEY (tenant_id, reverses_entry_id) REFERENCES app.inventory_entry (tenant_id, id),
    CHECK (entry_type <> 'REVERSAL' OR reverses_entry_id IS NOT NULL),
    CHECK (entry_type <> 'EXTRA_PULL' OR comment IS NOT NULL)      -- BR-037
);
CREATE UNIQUE INDEX inventory_entry_single_reversal ON app.inventory_entry (tenant_id, reverses_entry_id)
    WHERE reverses_entry_id IS NOT NULL;
COMMENT ON TABLE app.inventory_entry IS 'Append-only inventory ledger (URS-INV-001..006). Replaces legacy UNITS_PULLED/EXTRA_PULLED overwrite + restore.';

CREATE TABLE app.test_result (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    study_timepoint_id   uuid NOT NULL,
    spec_test_id         uuid NOT NULL,
    required             boolean NOT NULL DEFAULT true,     -- legacy TEST_INTERVAL_USED (Y/N)
    status               text NOT NULL DEFAULT 'PLANNED'
                         CHECK (status IN ('PLANNED','IN_PROGRESS','SUBMITTED','L1_APPROVED','APPROVED',
                                           'REJECTED','NOT_TESTED','CANCELLED')),
    analyst_user_id      uuid,
    analyst_code         text,                       -- legacy ANALYST dictionary value (migrated data)
    test_date            date,                       -- legacy RESULT_DATE
    reference            text,                       -- legacy REFERENCE (notebook / LIMS / CDS id)
    instrument_ref       text,
    reportable_numeric   numeric,                    -- unrounded reportable value (URS-RES-003/004)
    reportable_qualifier text CHECK (reportable_qualifier IN ('LT','GT','ND','LOQ','LOD','NT','NA')),
    reportable_text      text,
    reportable_display   text,                       -- rounded per USP <GN 7.20> at the spec precision
    reportable_algorithm text,                       -- e.g. results.mean@1.0.0
    oos                  boolean NOT NULL DEFAULT false,
    alert                boolean NOT NULL DEFAULT false,
    oot                  boolean NOT NULL DEFAULT false,
    oot_method           text,
    entered_by           uuid,
    submitted_at         timestamptz,
    approved_at          timestamptz,
    rejected_reason      text,
    legacy_average_text  text,                       -- legacy RESULT_AVERAGE as stored (parity checks)
    content_hash         bytea,
    FOREIGN KEY (tenant_id, study_timepoint_id) REFERENCES app.study_timepoint (tenant_id, id),
    FOREIGN KEY (tenant_id, spec_test_id) REFERENCES app.spec_test (tenant_id, id),
    UNIQUE (tenant_id, study_timepoint_id, spec_test_id)
);
CREATE INDEX test_result_review_idx ON app.test_result (tenant_id, status) WHERE status IN ('SUBMITTED','L1_APPROVED');
COMMENT ON TABLE app.test_result IS 'Legacy EA_PRD_STUDY_TEST_RESULTS (one row per time point x test). URS-RES-*, REV-*.';

CREATE TABLE app.result_replicate (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    test_result_id       uuid NOT NULL,
    replicate_no         int  NOT NULL CHECK (replicate_no >= 1),
    numeric_value        numeric,                    -- negative values allowed (BR-042 fixed)
    qualifier            text CHECK (qualifier IN ('LT','GT','ND','LOQ','LOD','NT','NA')),
    text_value           text,
    raw_input            text,                       -- exactly what was entered/imported
    FOREIGN KEY (tenant_id, test_result_id) REFERENCES app.test_result (tenant_id, id),
    UNIQUE (tenant_id, test_result_id, replicate_no),
    CHECK (numeric_value IS NOT NULL OR qualifier IS NOT NULL OR text_value IS NOT NULL)
);
COMMENT ON TABLE app.result_replicate IS 'Normalised legacy RESULT_1..30 (VARCHAR) into structured values (URS-RES-002).';

CREATE TABLE app.investigation (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    site_id              uuid NOT NULL,
    investigation_type   text NOT NULL CHECK (investigation_type IN ('OOS','OOT','DEVIATION','OTHER')),
    reference            text NOT NULL,              -- internal number or external QMS id
    external_system      text,                       -- e.g. VEEVA_QMS, TRACKWISE (1.1)
    status               text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    summary              text,
    opened_on            date NOT NULL,
    closed_on            date,
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);

CREATE TABLE app.investigation_result (
    tenant_id       uuid        NOT NULL,
    id              uuid        NOT NULL,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    deleted_at      timestamptz,
    deleted_by      uuid,
    delete_reason   text,
    origin          text        NOT NULL DEFAULT 'NATIVE' CHECK (origin IN ('NATIVE','ESTAR_MIGRATION','IMPORT')),
    PRIMARY KEY (tenant_id, id),
    CHECK (deleted_at IS NULL OR delete_reason IS NOT NULL),
    investigation_id     uuid NOT NULL,
    test_result_id       uuid NOT NULL,
    FOREIGN KEY (tenant_id, investigation_id) REFERENCES app.investigation (tenant_id, id),
    FOREIGN KEY (tenant_id, test_result_id) REFERENCES app.test_result (tenant_id, id),
    UNIQUE (tenant_id, investigation_id, test_result_id)
);
COMMENT ON TABLE app.investigation_result IS 'Links OOS/OOT results to investigations (URS-OOT-004).';

INSERT INTO platform.table_classification (table_name, classification) VALUES
 ('app.study_timepoint','GXP'), ('app.inventory_entry','GXP'), ('app.test_result','GXP'),
 ('app.result_replicate','GXP'), ('app.investigation','GXP'), ('app.investigation_result','GXP');
