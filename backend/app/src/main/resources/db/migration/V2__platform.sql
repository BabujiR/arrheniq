-- =============================================================================
-- V2: platform tables (cross-tenant; no RLS; not visible to arrheniq_app except
--      where explicitly granted in V900)
-- =============================================================================

CREATE TABLE platform.tenant (
    id              uuid PRIMARY KEY,
    slug            text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,40}$'),
    legal_name      text NOT NULL,
    display_name    text NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','EXPORTING','DELETED')),
    sandbox         boolean NOT NULL DEFAULT false,
    deployment      text NOT NULL DEFAULT 'CLOUD' CHECK (deployment IN ('CLOUD','DEDICATED','PRIVATE')),
    region          text NOT NULL DEFAULT 'us-east-1',
    idp_config      jsonb NOT NULL DEFAULT '{}'::jsonb,   -- issuer, client id ref, federation info (no secrets)
    data_key_ref    text,                                  -- KMS key / data key reference for crypto-shredding
    created_at      timestamptz NOT NULL DEFAULT now(),
    suspended_at    timestamptz,
    deleted_at      timestamptz
);
COMMENT ON TABLE platform.tenant IS 'Customer organisation = isolation boundary (multi-tenancy.md §1).';

CREATE TABLE platform.entitlement (
    tenant_id          uuid PRIMARY KEY REFERENCES platform.tenant(id),
    edition            text NOT NULL CHECK (edition IN ('STARTER','PROFESSIONAL','ENTERPRISE')),
    sites_included     int  NOT NULL CHECK (sites_included > 0),
    studies_included   int  NOT NULL CHECK (studies_included > 0),
    hard_limit         boolean NOT NULL DEFAULT false,
    addons             text[] NOT NULL DEFAULT '{}',      -- e.g. {PREDICT, CRO_PORTAL}
    valid_from         date NOT NULL,
    valid_to           date,
    updated_at         timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE platform.entitlement IS 'Plan entitlements (packaging-pricing.md; URS-ADM-003).';

CREATE TABLE platform.platform_audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    operator      text NOT NULL,
    action        text NOT NULL,
    tenant_id     uuid,
    details       jsonb NOT NULL DEFAULT '{}'::jsonb
);
COMMENT ON TABLE platform.platform_audit_log IS 'Operator actions (tenant provisioning, suspension, break-glass).';

-- -----------------------------------------------------------------------------
-- Reference catalog (read-only to tenants; copied into tenant dictionaries
-- at provisioning). Seeded in V800.
-- -----------------------------------------------------------------------------
CREATE TABLE platform.ref_dictionary (
    code            text PRIMARY KEY,
    name            text NOT NULL,
    description     text,
    category        text NOT NULL CHECK (category IN ('STABILITY','PACKAGING','RESULTS','ORGANISATION','SYSTEM')),
    legacy_code     text,                                -- eSTAR EA_DICTIONARY_MASTER.DICTIONARY_CODE
    allow_tenant_values boolean NOT NULL DEFAULT true
);

CREATE TABLE platform.ref_dictionary_value (
    dictionary_code text NOT NULL REFERENCES platform.ref_dictionary(code),
    code            text NOT NULL,
    label           text NOT NULL,
    description     text,
    sort_order      int  NOT NULL DEFAULT 0,
    attributes      jsonb NOT NULL DEFAULT '{}'::jsonb,  -- e.g. temperature, humidity for conditions
    PRIMARY KEY (dictionary_code, code)
);

CREATE TABLE platform.ref_protocol_template (
    code            text PRIMARY KEY,
    name            text NOT NULL,
    study_type      text NOT NULL,
    condition_code  text NOT NULL,
    timepoint_unit  text NOT NULL CHECK (timepoint_unit IN ('DAY','WEEK','MONTH')),
    timepoints      numeric[] NOT NULL,
    source          text NOT NULL,                       -- guideline reference
    note            text
);

-- -----------------------------------------------------------------------------
-- Background jobs (db-scheduler; ADR 0008). DDL per db-scheduler PostgreSQL
-- schema; VERIFY against the library version pinned in the build.
-- -----------------------------------------------------------------------------
CREATE TABLE platform.scheduled_tasks (
    task_name            text NOT NULL,
    task_instance        text NOT NULL,
    task_data            bytea,
    execution_time       timestamptz NOT NULL,
    picked               boolean NOT NULL,
    picked_by            text,
    last_success         timestamptz,
    last_failure         timestamptz,
    consecutive_failures int,
    last_heartbeat       timestamptz,
    version              bigint NOT NULL,
    priority             smallint,
    PRIMARY KEY (task_name, task_instance)
);
CREATE INDEX scheduled_tasks_execution_time_idx ON platform.scheduled_tasks (execution_time);
CREATE INDEX scheduled_tasks_last_heartbeat_idx ON platform.scheduled_tasks (last_heartbeat);
CREATE INDEX scheduled_tasks_priority_execution_time_idx ON platform.scheduled_tasks (priority DESC, execution_time ASC);

-- -----------------------------------------------------------------------------
-- HTTP sessions (Spring Session JDBC; ADR 0006). VERIFY against pinned version.
-- -----------------------------------------------------------------------------
CREATE TABLE platform.spring_session (
    primary_id            char(36) NOT NULL PRIMARY KEY,
    session_id            char(36) NOT NULL,
    creation_time         bigint   NOT NULL,
    last_access_time      bigint   NOT NULL,
    max_inactive_interval int      NOT NULL,
    expiry_time           bigint   NOT NULL,
    principal_name        varchar(100)
);
CREATE UNIQUE INDEX spring_session_ix1 ON platform.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON platform.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON platform.spring_session (principal_name);

CREATE TABLE platform.spring_session_attributes (
    session_primary_id char(36)     NOT NULL REFERENCES platform.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

INSERT INTO platform.table_classification (table_name, classification, hard_delete, note) VALUES
 ('platform.tenant',                    'PLATFORM', false, NULL),
 ('platform.entitlement',               'PLATFORM', false, NULL),
 ('platform.platform_audit_log',        'PLATFORM', false, 'append-only by convention'),
 ('platform.ref_dictionary',            'PLATFORM', false, NULL),
 ('platform.ref_dictionary_value',      'PLATFORM', false, NULL),
 ('platform.ref_protocol_template',     'PLATFORM', false, NULL),
 ('platform.scheduled_tasks',           'PLATFORM', true,  'job queue'),
 ('platform.spring_session',            'PLATFORM', true,  'sessions'),
 ('platform.spring_session_attributes', 'PLATFORM', true,  'sessions'),
 ('platform.table_classification',      'PLATFORM', false, NULL);
