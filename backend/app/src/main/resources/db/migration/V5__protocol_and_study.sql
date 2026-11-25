-- =============================================================================
-- V5: protocols (versioned) and studies
-- URS-PRO-*, URS-STU-*; legacy EA_PRD_PROTOCOLS, EA_PRD_PROTOCOL_DETAILS,
-- EA_PRD_INVENTORY, EA_PRD_PRODUCT_STUDY, EA_PRD_STUDY_CONDITIONS,
-- EA_PRD_STUDY_RAW_MATERIALS, EA_PRD_STUDY_ATTACHMENTS
-- =============================================================================

CREATE TABLE app.protocol (
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
    protocol_number      text NOT NULL,
    name                 text NOT NULL,
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id),
    UNIQUE (tenant_id, protocol_number)
);

CREATE TABLE app.protocol_version (
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
    protocol_id          uuid NOT NULL,
    version_no           int  NOT NULL CHECK (version_no > 0),
    status               text NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','SUPERSEDED','RETIRED')),
    study_type           text NOT NULL CHECK (study_type IN ('LONG_TERM','INTERMEDIATE','ACCELERATED','STRESS',
                                                             'PHOTOSTABILITY','IN_USE','EXCURSION','COMMITMENT','ANNUAL','OTHER')),
    design_type          text NOT NULL DEFAULT 'FULL' CHECK (design_type IN ('FULL','BRACKETING','MATRIXING','BRACKETING_MATRIXING')),
    design_justification text,                       -- URS-PRO-004 (1.1)
    reserve_units        int NOT NULL DEFAULT 0 CHECK (reserve_units >= 0),
    fill_unit_code       text,
    content_hash         bytea,
    FOREIGN KEY (tenant_id, protocol_id) REFERENCES app.protocol (tenant_id, id),
    UNIQUE (tenant_id, protocol_id, version_no)
);
COMMENT ON TABLE app.protocol_version IS 'Versioned protocol (URS-PRO-005). Legacy protocols had max 20 time points.';

CREATE TABLE app.protocol_timepoint (
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
    protocol_version_id  uuid NOT NULL,
    seq                  int  NOT NULL CHECK (seq >= 0),
    value                numeric NOT NULL CHECK (value >= 0),
    unit                 text NOT NULL CHECK (unit IN ('DAY','WEEK','MONTH')),
    label                text,                       -- e.g. "Initial", "12M"
    units_planned        int NOT NULL DEFAULT 0 CHECK (units_planned >= 0),   -- legacy TEST_INTERVAL_INVENTORYn
    FOREIGN KEY (tenant_id, protocol_version_id) REFERENCES app.protocol_version (tenant_id, id),
    UNIQUE (tenant_id, protocol_version_id, seq)
);
COMMENT ON TABLE app.protocol_timepoint IS 'Normalised legacy TEST_INTERVAL_VALUE1..20 (URS-PRO-001/003).';

CREATE TABLE app.protocol_test (
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
    protocol_version_id  uuid NOT NULL,
    test_method_id       uuid NOT NULL,
    sort_order           int NOT NULL,
    FOREIGN KEY (tenant_id, protocol_version_id) REFERENCES app.protocol_version (tenant_id, id),
    FOREIGN KEY (tenant_id, test_method_id) REFERENCES app.test_method (tenant_id, id),
    UNIQUE (tenant_id, protocol_version_id, test_method_id)
);

CREATE TABLE app.protocol_matrix_cell (
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
    protocol_test_id     uuid NOT NULL,
    protocol_timepoint_id uuid NOT NULL,
    required             boolean NOT NULL DEFAULT true,     -- legacy TEST_INTERVAL_USEDn = 'Y'
    FOREIGN KEY (tenant_id, protocol_test_id) REFERENCES app.protocol_test (tenant_id, id),
    FOREIGN KEY (tenant_id, protocol_timepoint_id) REFERENCES app.protocol_timepoint (tenant_id, id),
    UNIQUE (tenant_id, protocol_test_id, protocol_timepoint_id)
);
COMMENT ON TABLE app.protocol_matrix_cell IS 'Test x time-point matrix (URS-PRO-002); basis for matrixing.';

CREATE TABLE app.study_group (
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
    lot_number           text NOT NULL,
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id)
);
COMMENT ON TABLE app.study_group IS 'One study login creating several studies (one per condition) for a lot (URS-STU-004).';

CREATE TABLE app.study (
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
    study_group_id       uuid,
    study_code           text NOT NULL,              -- legacy STUDY_ID (YYnnnn)
    proprietary_study_id text,                       -- legacy PROPREITARY_STUDY_ID
    product_id           uuid NOT NULL,
    spec_version_id      uuid NOT NULL,
    protocol_version_id  uuid NOT NULL,
    lot_number           text NOT NULL,
    formula_number       text,
    batch_size_text      text,
    manufacture_date     date,
    package_date         date,
    expiry_date          date,
    on_stability_date    date NOT NULL,              -- T0; legacy ON_STABILITY_DATE / INTIAL_TEST_DATE
    condition_code       text NOT NULL,              -- dictionary STORAGE_CONDITION
    challenge_condition_code text,                   -- legacy CHALLENGE_CONDITIONS_CODE
    reason_code          text NOT NULL,              -- dictionary STUDY_REASON
    study_length_code    text NOT NULL,              -- dictionary STUDY_LENGTH
    storage_location_code text NOT NULL,             -- dictionary STORAGE_LOCATION
    fill_quantity        numeric,                    -- legacy FILL_UNITS
    fill_unit_code       text,
    units_placed         int NOT NULL DEFAULT 0 CHECK (units_placed >= 0),   -- legacy TOTAL_VALUE
    reserve_units        int NOT NULL DEFAULT 0 CHECK (reserve_units >= 0),  -- legacy EXTRA_VALUE
    label_template_id    uuid,
    label_color          text,
    status               text NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','COMPLETED','TERMINATED')),
    approved_at          timestamptz,
    back_entry           boolean NOT NULL DEFAULT false,      -- URS-STU-007
    back_entry_attested_by uuid,
    back_entry_attested_at timestamptz,
    terminated_reason    text,
    study_text           text,                        -- legacy STUDY_TEXT
    custom_fields        jsonb NOT NULL DEFAULT '{}'::jsonb,  -- legacy USER_DEFINED_COLUMN_1..10
    content_hash         bytea,
    UNIQUE (tenant_id, study_code),
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id),
    FOREIGN KEY (tenant_id, study_group_id) REFERENCES app.study_group (tenant_id, id),
    FOREIGN KEY (tenant_id, product_id) REFERENCES app.product (tenant_id, id),
    FOREIGN KEY (tenant_id, spec_version_id) REFERENCES app.spec_version (tenant_id, id),
    FOREIGN KEY (tenant_id, protocol_version_id) REFERENCES app.protocol_version (tenant_id, id),
    CHECK (back_entry = false OR status IN ('DRAFT','IN_REVIEW') OR back_entry_attested_by IS NOT NULL)
);
CREATE INDEX study_product_idx ON app.study (tenant_id, product_id);
CREATE INDEX study_status_idx  ON app.study (tenant_id, site_id, status);
COMMENT ON TABLE app.study IS 'Lot x condition on stability. Legacy EA_PRD_PRODUCT_STUDY + EA_PRD_STUDY_CONDITIONS (1:1). URS-STU-001..009.';

CREATE TABLE app.study_component (
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
    component_type       text NOT NULL CHECK (component_type IN (
                           'PACKAGE_SIZE','CONTAINER','CONTAINER_RESIN','CLOSURE','LINER','INNERSEAL','DESICCANT',
                           'FILLER','BLISTER','LIDDING_FOIL','FORMING_FOIL','OTHER_1','OTHER_2',
                           'MANUFACTURING_SITE','PACKAGING_SITE')),
    component_code       text NOT NULL,              -- dictionary value code
    manufacturer_code    text,                       -- dictionary SUPPLIER / *_MANUFACTURER
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    UNIQUE (tenant_id, study_id, component_type)
);
COMMENT ON TABLE app.study_component IS 'Normalised 20 packaging/site columns of EA_PRD_PRODUCT_STUDY (URS-STU-003).';

CREATE TABLE app.study_raw_material (
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
    formulation_line_id  uuid,
    material_name        text NOT NULL,
    lot_number           text,
    supplier_code        text,
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    FOREIGN KEY (tenant_id, formulation_line_id) REFERENCES app.product_formulation_line (tenant_id, id)
);
COMMENT ON TABLE app.study_raw_material IS 'Legacy EA_PRD_STUDY_RAW_MATERIALS.';

CREATE TABLE app.study_attachment (
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
    file_name            text NOT NULL,
    description          text,
    content_type         text NOT NULL,
    size_bytes           bigint NOT NULL CHECK (size_bytes >= 0),
    object_key           text NOT NULL,              -- server-generated (ADR 0010)
    sha256               bytea NOT NULL,
    malware_scan         text NOT NULL DEFAULT 'PENDING' CHECK (malware_scan IN ('PENDING','CLEAN','REJECTED')),
    FOREIGN KEY (tenant_id, study_id) REFERENCES app.study (tenant_id, id),
    UNIQUE (tenant_id, object_key)
);
COMMENT ON TABLE app.study_attachment IS 'Legacy EA_PRD_STUDY_ATTACHMENTS (BLOB) → object storage (URS-STU-008, SEC-004).';

INSERT INTO platform.table_classification (table_name, classification) VALUES
 ('app.protocol','GXP'), ('app.protocol_version','GXP'), ('app.protocol_timepoint','GXP'), ('app.protocol_test','GXP'),
 ('app.protocol_matrix_cell','GXP'), ('app.study_group','GXP'), ('app.study','GXP'), ('app.study_component','GXP'),
 ('app.study_raw_material','GXP'), ('app.study_attachment','GXP');
