-- =============================================================================
-- V4: master data: test library, products, specifications (versioned)
-- URS-MD-001..008; legacy EA_PRD_TESTS, EA_PRD_PRODUCTS, EA_PRD_PRODUCT_TESTS,
-- EA_PRD_PRODUCT_SPECS, EA_PRD_PRODUCT_FORMULATION, EA_PRD_PRODUCT_BATCHES
-- =============================================================================

CREATE TABLE app.test_method (
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
    method_reference     text,                       -- legacy METHOD
    lab_number           text,                       -- legacy LAB_NUMBER
    results_format       text NOT NULL CHECK (results_format IN ('NUMERIC','TEXT')),    -- legacy V/T
    unit_code            text,                       -- dictionary MEASUREMENT
    replicates           int  NOT NULL DEFAULT 1 CHECK (replicates BETWEEN 1 AND 100),  -- legacy NUM_OF_ENTRIES (max 30)
    decimals             int  CHECK (decimals BETWEEN 0 AND 10),                          -- legacy DECIMAL_POINTS
    default_lower_limit  numeric,
    default_upper_limit  numeric,
    default_text_limit   text,
    default_alert_lower  numeric,
    default_alert_upper  numeric,
    default_variance_lower_pct numeric,             -- legacy ALERT_LOWER_VARIANCE
    default_variance_upper_pct numeric,             -- legacy ALERT_UPPER_VARIANCE
    trendable            boolean NOT NULL DEFAULT true,   -- legacy GRAPH (Y/N)
    status               text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RETIRED')),
    UNIQUE (tenant_id, code)
);
COMMENT ON TABLE app.test_method IS 'Test library (legacy EA_PRD_TESTS). URS-MD-001.';

CREATE TABLE app.product (
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
    code                 text NOT NULL,
    name                 text NOT NULL,
    description          text,
    product_type         text NOT NULL CHECK (product_type IN ('DRUG_SUBSTANCE','DRUG_PRODUCT','BIOLOGIC_DS','BIOLOGIC_DP','EXCIPIENT','OTHER')),
    dosage_form          text,
    strength             text,
    expiration_value     int CHECK (expiration_value > 0),        -- legacy EXPIRATION_PERIOD (free text) → structured
    expiration_unit      text CHECK (expiration_unit IN ('DAY','WEEK','MONTH')),
    expiration_status    text NOT NULL DEFAULT 'ESTABLISHED' CHECK (expiration_status IN ('ESTABLISHED','TO_BE_DETERMINED','NOT_APPLICABLE')),
    controlled_substance boolean NOT NULL DEFAULT false,
    annual_report_anniversary date,                                 -- legacy ANNUAL_RPT_ANNIVERSARY_DATE
    status               text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','RETIRED')),
    custom_fields        jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id),
    CHECK ((expiration_status = 'ESTABLISHED') = (expiration_value IS NOT NULL AND expiration_unit IS NOT NULL))
);
COMMENT ON TABLE app.product IS 'Legacy EA_PRD_PRODUCTS. URS-MD-002.';

CREATE TABLE app.product_formulation_line (
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
    product_id           uuid NOT NULL,
    line_no              int  NOT NULL,
    ingredient           text NOT NULL,
    role                 text,                       -- API, filler, binder...
    quantity_text        text,
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id),
    UNIQUE (tenant_id, product_id, line_no)
);
COMMENT ON TABLE app.product_formulation_line IS 'Legacy EA_PRD_PRODUCT_FORMULATION. URS-MD-008.';

CREATE TABLE app.product_batch_size (
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
    product_id           uuid NOT NULL,
    line_no              int  NOT NULL,
    batch_size_text      text NOT NULL,
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id),
    UNIQUE (tenant_id, product_id, line_no)
);
COMMENT ON TABLE app.product_batch_size IS 'Legacy EA_PRD_PRODUCT_BATCHES.';

CREATE TABLE app.specification (
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
    product_id           uuid NOT NULL,
    spec_number          text NOT NULL,
    title                text NOT NULL,
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id),
    UNIQUE (tenant_id, spec_number)
);
COMMENT ON TABLE app.specification IS 'Stable identity of a product specification; content lives in spec_version.';

CREATE TABLE app.spec_version (
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
    specification_id     uuid NOT NULL,
    version_no           int  NOT NULL CHECK (version_no > 0),
    status               text NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','EFFECTIVE','SUPERSEDED','RETIRED')),
    effective_from       date,
    change_summary       text,
    spec_text            text,                       -- legacy EA_PRD_PRODUCT_SPECS lines, joined
    content_hash         bytea,                      -- RecordHasher at approval (signature binding)
    FOREIGN KEY (tenant_id, specification_id) REFERENCES app.specification (tenant_id, id),
    UNIQUE (tenant_id, specification_id, version_no)
);
CREATE UNIQUE INDEX spec_version_one_effective ON app.spec_version (tenant_id, specification_id)
    WHERE status = 'EFFECTIVE' AND deleted_at IS NULL;
COMMENT ON TABLE app.spec_version IS 'Versioned specification (URS-MD-004). One EFFECTIVE version per spec.';

CREATE TABLE app.spec_test (
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
    spec_version_id      uuid NOT NULL,
    test_method_id       uuid NOT NULL,
    sort_order           int  NOT NULL,
    display_name         text NOT NULL,
    results_format       text NOT NULL CHECK (results_format IN ('NUMERIC','TEXT')),
    unit_code            text,
    replicates           int  NOT NULL CHECK (replicates BETWEEN 1 AND 100),
    decimals             int  CHECK (decimals BETWEEN 0 AND 10),
    lower_limit          numeric,
    upper_limit          numeric,
    text_limit           text,
    alert_lower          numeric,
    alert_upper          numeric,
    variance_lower_pct   numeric,
    variance_upper_pct   numeric,
    replicate_rule       jsonb,                      -- replicate-level acceptance (e.g. dissolution stages), URS-RES-005
    trendable            boolean NOT NULL DEFAULT true,
    FOREIGN KEY (tenant_id, spec_version_id) REFERENCES app.spec_version (tenant_id, id),
    FOREIGN KEY (tenant_id, test_method_id)  REFERENCES app.test_method  (tenant_id, id),
    UNIQUE (tenant_id, spec_version_id, test_method_id),
    CHECK (lower_limit IS NULL OR upper_limit IS NULL OR lower_limit <= upper_limit),
    CHECK (alert_lower IS NULL OR lower_limit IS NULL OR alert_lower >= lower_limit),
    CHECK (alert_upper IS NULL OR upper_limit IS NULL OR alert_upper <= upper_limit)
);
COMMENT ON TABLE app.spec_test IS 'Legacy EA_PRD_PRODUCT_TESTS (limits as numeric; were VARCHAR). URS-MD-003.';

INSERT INTO platform.table_classification (table_name, classification) VALUES
 ('app.test_method','GXP'), ('app.product','GXP'), ('app.product_formulation_line','GXP'),
 ('app.product_batch_size','GXP'), ('app.specification','GXP'), ('app.spec_version','GXP'), ('app.spec_test','GXP');
