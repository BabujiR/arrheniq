-- =============================================================================
-- V8: imports/exports, outbox, signing intents, migration bookkeeping
-- URS-RES-007, INT-003, MIG-001..003, ESG-002
-- =============================================================================

CREATE TABLE app.import_job (
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
    import_type          text NOT NULL CHECK (import_type IN ('RESULTS','TEST_LIBRARY','PRODUCTS','DICTIONARY')),
    file_name            text NOT NULL,
    object_key           text NOT NULL,
    sha256               bytea NOT NULL,
    status               text NOT NULL DEFAULT 'UPLOADED'
                         CHECK (status IN ('UPLOADED','VALIDATED','APPLIED','REJECTED','CANCELLED')),
    rows_total           int,
    rows_valid           int,
    rows_rejected        int,
    validation_report    jsonb
);
COMMENT ON TABLE app.import_job IS 'CSV/XLSX imports with preview before apply (URS-RES-007).';

CREATE TABLE app.export_job (
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
    export_type          text NOT NULL CHECK (export_type IN ('FULL_TENANT','AUDIT_TRAIL','STUDY','CUSTOM')),
    parameters           jsonb NOT NULL DEFAULT '{}'::jsonb,
    status               text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','EXPIRED')),
    object_key           text,
    sha256               bytea,
    expires_at           timestamptz
);
COMMENT ON TABLE app.export_job IS 'Data exports, themselves audited (URS-INT-003, AUD-006).';

CREATE TABLE app.outbox_event (
    tenant_id            uuid NOT NULL,
    id                   uuid NOT NULL,
    occurred_at          timestamptz NOT NULL DEFAULT now(),
    event_type           text NOT NULL,
    aggregate_type       text NOT NULL,
    aggregate_id         uuid NOT NULL,
    payload              jsonb NOT NULL,
    dispatched_at        timestamptz,
    attempts             int NOT NULL DEFAULT 0,
    last_error           text,
    PRIMARY KEY (tenant_id, id)
);
CREATE INDEX outbox_pending_idx ON app.outbox_event (occurred_at) WHERE dispatched_at IS NULL;
COMMENT ON TABLE app.outbox_event IS 'Transactional outbox (ADR 0008).';

CREATE TABLE app.signing_intent (
    tenant_id            uuid NOT NULL,
    id                   uuid NOT NULL,
    user_id              uuid NOT NULL,
    record_type          text NOT NULL,
    record_ids           uuid[] NOT NULL,            -- batch approval under one re-authentication
    record_versions      bigint[] NOT NULL,
    content_hashes       bytea[] NOT NULL,
    meaning              text NOT NULL,
    nonce                text NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    expires_at           timestamptz NOT NULL,
    consumed_at          timestamptz,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, nonce),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app.app_user (tenant_id, id),
    CHECK (cardinality(record_ids) = cardinality(record_versions)
       AND cardinality(record_ids) = cardinality(content_hashes))
);
COMMENT ON TABLE app.signing_intent IS 'Server-side signing intents for re-authentication (audit-esignature.md §9.2).';

CREATE TABLE app.migration_run (
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
    source_system        text NOT NULL DEFAULT 'ESTAR',
    source_description   text NOT NULL,              -- e.g. "eSTAR Oracle export 2027-06-01, site NJ"
    source_checksum      bytea,
    mode                 text NOT NULL CHECK (mode IN ('DRY_RUN','PRODUCTION')),
    status               text NOT NULL DEFAULT 'STARTED'
                         CHECK (status IN ('STARTED','EXTRACTED','TRANSFORMED','LOADED','RECONCILED','FAILED','SIGNED_OFF')),
    tool_version         text NOT NULL,
    mapping_version      text NOT NULL,
    reconciliation       jsonb,                      -- counts, checksums, exceptions
    report_object_key    text,
    finished_at          timestamptz
);
COMMENT ON TABLE app.migration_run IS 'eSTAR migration runs + reconciliation (URS-MIG-001/002).';

CREATE TABLE app.legacy_id_map (
    tenant_id            uuid NOT NULL,
    migration_run_id     uuid NOT NULL,
    legacy_table         text NOT NULL,              -- e.g. EA_PRD_PRODUCT_STUDY
    legacy_key           text NOT NULL,              -- e.g. PRD_STUDY_BATCH_ID value (composite keys joined with '|')
    target_table         text NOT NULL,
    target_id            uuid NOT NULL,
    PRIMARY KEY (tenant_id, legacy_table, legacy_key, target_table),
    FOREIGN KEY (tenant_id, migration_run_id) REFERENCES app.migration_run (tenant_id, id)
);
CREATE INDEX legacy_id_map_target_idx ON app.legacy_id_map (tenant_id, target_table, target_id);
COMMENT ON TABLE app.legacy_id_map IS 'Legacy IDs searchable after migration (URS-MIG-003).';

INSERT INTO platform.table_classification (table_name, classification, hard_delete) VALUES
 ('app.import_job','GXP',false), ('app.export_job','GXP',false), ('app.outbox_event','NON_GXP',true),
 ('app.signing_intent','SECURITY',false), ('app.migration_run','GXP',false), ('app.legacy_id_map','GXP',false);
