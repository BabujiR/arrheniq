-- =============================================================================
-- V7: evaluations, generated reports, report preferences, labels, notifications
-- URS-EVA-*, RPT-*, LBL-*, SCH-006
-- =============================================================================

CREATE TABLE app.evaluation (
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
    evaluation_type      text NOT NULL CHECK (evaluation_type IN
                           ('SINGLE_REGRESSION','POOLABILITY','SHELF_LIFE','SUMMARY_STATS','OOT','ARRHENIUS','ASAP')),
    title                text NOT NULL,
    algorithm_id         text NOT NULL,              -- e.g. q1e.poolability
    algorithm_version    text NOT NULL,              -- semantic version (ADR 0011)
    parity_mode          boolean NOT NULL DEFAULT false,   -- legacy eSTAR parity (URS-EVA-010)
    input_snapshot       jsonb NOT NULL,             -- immutable input (values, times, limits, params)
    input_hash           bytea NOT NULL,
    output               jsonb,                      -- results, model chosen, p-values, shelf life
    output_hash          bytea,
    chart_object_key     text,                       -- SVG rendered by stats service
    status               text NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED','FAILED','SIGNED','SUPERSEDED')),
    error                jsonb,                      -- explicit errors (URS-EVA-004)
    content_hash         bytea,
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);
CREATE TABLE app.evaluation_study (
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
    evaluation_id        uuid NOT NULL,
    study_id             uuid NOT NULL,
    FOREIGN KEY (tenant_id, evaluation_id) REFERENCES app.evaluation (tenant_id, id),
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    UNIQUE (tenant_id, evaluation_id, study_id)
);
COMMENT ON TABLE app.evaluation IS 'Versioned, reproducible statistical evaluations (URS-EVA-005).';

CREATE TABLE app.generated_report (
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
    site_id              uuid,
    report_code          text NOT NULL,              -- e.g. STABILITY_SUMMARY
    report_version       text NOT NULL,              -- template version
    document_number      text NOT NULL,              -- unique per tenant (URS-RPT-002)
    title                text NOT NULL,
    format               text NOT NULL CHECK (format IN ('PDF','XLSX','DOCX','CSV')),
    parameters           jsonb NOT NULL,
    object_key           text,
    sha256               bytea,
    size_bytes           bigint,
    status               text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','SIGNED')),
    generated_at         timestamptz,
    error                text,
    content_hash         bytea,
    UNIQUE (tenant_id, document_number),
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);
COMMENT ON TABLE app.generated_report IS 'Immutable generated reports with hash (URS-RPT-005).';

CREATE TABLE app.report_preference (
    tenant_id            uuid NOT NULL,
    id                   uuid NOT NULL,
    user_id              uuid NOT NULL,
    report_code          text NOT NULL,
    preferences          jsonb NOT NULL,             -- columns, order, sort (legacy EA_RPT_USER_PREFERENCE)
    updated_at           timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, user_id, report_code),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app.app_user (tenant_id, id)
);

CREATE TABLE app.label_template (
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
    code                 text NOT NULL,
    name                 text NOT NULL,
    layout               text NOT NULL CHECK (layout IN ('SMALL_2D','STANDARD_1D','LARGE_2D','CUSTOM')),
    fields               jsonb NOT NULL,             -- ordered field list
    barcode_symbology    text NOT NULL DEFAULT 'DATAMATRIX' CHECK (barcode_symbology IN ('DATAMATRIX','QR','CODE128')),
    status               text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RETIRED')),
    UNIQUE (tenant_id, code)
);
ALTER TABLE app.study ADD FOREIGN KEY (tenant_id, label_template_id) REFERENCES app.label_template (tenant_id, id);
COMMENT ON TABLE app.label_template IS 'Legacy EA_PRD_LABELS (fixed templates in MVP; designer in 1.1).';

CREATE TABLE app.label_print_job (
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
    label_template_id    uuid NOT NULL,
    reprint              boolean NOT NULL DEFAULT false,
    reason               text,
    label_count          int NOT NULL CHECK (label_count > 0),
    details              jsonb NOT NULL,             -- per time point counts incl. extras
    object_key           text,
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    FOREIGN KEY (tenant_id, label_template_id) REFERENCES app.label_template (tenant_id, id),
    CHECK (reprint = false OR reason IS NOT NULL)
);
COMMENT ON TABLE app.label_print_job IS 'Legacy EA_LABEL_PRINT_REQUESTS(+_DETAILS). Print log (URS-LBL-002).';

CREATE TABLE app.notification (
    tenant_id            uuid NOT NULL,
    id                   uuid NOT NULL,
    user_id              uuid NOT NULL,
    kind                 text NOT NULL,              -- PULL_DUE, REVIEW_REQUESTED, OOS_RAISED, ...
    payload              jsonb NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    read_at              timestamptz,
    emailed_at           timestamptz,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES app.app_user (tenant_id, id)
);

INSERT INTO platform.table_classification (table_name, classification, hard_delete) VALUES
 ('app.evaluation','GXP',false), ('app.evaluation_study','GXP',false), ('app.generated_report','GXP',false),
 ('app.report_preference','NON_GXP',true), ('app.label_template','GXP',false), ('app.label_print_job','GXP',false),
 ('app.notification','NON_GXP',true);
