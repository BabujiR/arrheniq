-- =============================================================================
-- V3: identity, sites, roles, configuration, sequences, dictionaries
-- GxP tables use the standard record columns (tenant_id, id, version, created_*, updated_*, deleted_*, origin).
-- Legacy mapping: docs/data/legacy-mapping.md
-- =============================================================================

CREATE TABLE app.site (
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
    code            text NOT NULL,
    name            text NOT NULL,
    time_zone       text NOT NULL DEFAULT 'America/New_York',   -- IANA zone (ADR 0012)
    locale          text NOT NULL DEFAULT 'en-US',
    address         jsonb NOT NULL DEFAULT '{}'::jsonb,
    holiday_calendar jsonb NOT NULL DEFAULT '[]'::jsonb,         -- list of ISO dates (URS-SCH-002)
    active          boolean NOT NULL DEFAULT true,
    UNIQUE (tenant_id, code)
);
COMMENT ON TABLE app.site IS 'Legacy EA_LOCATIONS. Authorisation scope inside a tenant (URS-TEN-002/003).';

CREATE TABLE app.app_user (
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
    idp_issuer      text NOT NULL,
    idp_subject     text NOT NULL,
    username        text NOT NULL,
    email           text NOT NULL,
    display_name    text NOT NULL,       -- printed name used in signatures (Part 11 §11.50)
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('INVITED','ACTIVE','DEACTIVATED')),
    default_site_id uuid,
    last_login_at   timestamptz,
    deactivated_at  timestamptz,
    UNIQUE (tenant_id, username),
    UNIQUE (tenant_id, idp_issuer, idp_subject),
    FOREIGN KEY (tenant_id, default_site_id) REFERENCES app.site (tenant_id, id)
);
COMMENT ON TABLE app.app_user IS 'Legacy EA_APP_USER (no passwords stored: URS-SEC-003). Usernames never reused (URS-IAM-001).';

CREATE TABLE app.role (
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
    code            text NOT NULL,
    name            text NOT NULL,
    description     text,
    preset          boolean NOT NULL DEFAULT false,
    UNIQUE (tenant_id, code)
);
COMMENT ON TABLE app.role IS 'Legacy EA_APP_ACCESS_TEMPLATE (user groups) → permission bundles (URS-IAM-005/006).';

CREATE TABLE app.role_permission (
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
    role_id         uuid NOT NULL,
    permission      text NOT NULL,                  -- e.g. result.approve.final
    UNIQUE (tenant_id, role_id, permission),
    FOREIGN KEY (tenant_id, role_id) REFERENCES app.role (tenant_id, id)
);

CREATE TABLE app.user_site_role (
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
    user_id         uuid NOT NULL,
    role_id         uuid NOT NULL,
    site_id         uuid,                           -- NULL = all sites
    valid_from      date NOT NULL DEFAULT current_date,
    valid_to        date,
    FOREIGN KEY (tenant_id, user_id) REFERENCES app.app_user (tenant_id, id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES app.role (tenant_id, id),
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);
CREATE UNIQUE INDEX user_site_role_uq ON app.user_site_role
    (tenant_id, user_id, role_id, COALESCE(site_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;
COMMENT ON TABLE app.user_site_role IS 'Legacy EA_APP_USER_MODULE_ACCESS + EA_APP_USER_LOCATIONS.';

CREATE TABLE app.tenant_setting (
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
    setting_key     text NOT NULL,                  -- e.g. scheduling.pull_window_days
    site_id         uuid,                           -- NULL = tenant default
    value           jsonb NOT NULL,
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);
CREATE UNIQUE INDEX tenant_setting_uq ON app.tenant_setting
    (tenant_id, setting_key, COALESCE(site_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;
COMMENT ON TABLE app.tenant_setting IS 'Legacy EA_APPL_PARAMETER (28 parameters, business-rules.md). Versioned + audited (URS-ADM-001/002).';

CREATE TABLE app.tenant_sequence (
    tenant_id       uuid NOT NULL,
    name            text NOT NULL,                  -- e.g. STUDY_CODE
    scope_key       text NOT NULL DEFAULT '',       -- e.g. '2026' for per-year study IDs
    next_value      bigint NOT NULL DEFAULT 1 CHECK (next_value > 0),
    PRIMARY KEY (tenant_id, name, scope_key)
);
COMMENT ON TABLE app.tenant_sequence IS 'Per-tenant business sequences, row-locked (URS-STU-002; legacy EA_STUDY_ID_MASTER).';

CREATE TABLE app.api_key (
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
    name            text NOT NULL,
    key_prefix      text NOT NULL,                  -- first 8 chars, shown to users
    key_hash        text NOT NULL,                  -- Argon2id hash
    scopes          text[] NOT NULL,
    site_ids        uuid[],                         -- NULL = all sites
    expires_at      timestamptz,
    revoked_at      timestamptz,
    last_used_at    timestamptz,
    UNIQUE (tenant_id, key_prefix)
);

CREATE TABLE app.dictionary (
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
    code            text NOT NULL,                  -- e.g. STORAGE_CONDITION
    name            text NOT NULL,
    category        text NOT NULL,
    system_managed  boolean NOT NULL DEFAULT false, -- true: values come from the catalog only
    UNIQUE (tenant_id, code)
);
COMMENT ON TABLE app.dictionary IS 'Legacy EA_DICTIONARY_MASTER.';

CREATE TABLE app.dictionary_value (
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
    dictionary_id   uuid NOT NULL,
    site_id         uuid,                           -- NULL = all sites (legacy LOCATION_ID)
    code            text NOT NULL,
    label           text NOT NULL,
    description     text,
    sort_order      int NOT NULL DEFAULT 0,
    attributes      jsonb NOT NULL DEFAULT '{}'::jsonb,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RETIRED')),   -- retire, never delete (URS-MD-006)
    FOREIGN KEY (tenant_id, dictionary_id) REFERENCES app.dictionary (tenant_id, id),
    FOREIGN KEY (tenant_id, site_id) REFERENCES app.site (tenant_id, id)
);
CREATE UNIQUE INDEX dictionary_value_uq ON app.dictionary_value
    (tenant_id, dictionary_id, code, COALESCE(site_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;
COMMENT ON TABLE app.dictionary_value IS 'Legacy EA_DICTIONARY_DETAIL (+ EA_DEFAULT_DICTIONARY_DETAIL via catalog).';

INSERT INTO platform.table_classification (table_name, classification, hard_delete, note) VALUES
 ('app.site',             'GXP',      false, NULL),
 ('app.app_user',         'SECURITY', false, NULL),
 ('app.role',             'SECURITY', false, NULL),
 ('app.role_permission',  'SECURITY', false, NULL),
 ('app.user_site_role',   'SECURITY', false, NULL),
 ('app.tenant_setting',   'GXP',      false, NULL),
 ('app.tenant_sequence',  'NON_GXP',  false, 'technical counter; values appear in audited records'),
 ('app.api_key',          'SECURITY', false, NULL),
 ('app.dictionary',       'GXP',      false, NULL),
 ('app.dictionary_value', 'GXP',      false, NULL);
