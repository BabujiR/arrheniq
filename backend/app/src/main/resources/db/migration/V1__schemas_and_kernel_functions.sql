-- =============================================================================
-- V1: schemas and kernel functions
-- Design: docs/architecture/multi-tenancy.md, audit-esignature.md; ADR 0003, 0004
-- =============================================================================

CREATE SCHEMA platform;   -- cross-tenant: tenants, plans, reference catalog, jobs, sessions
CREATE SCHEMA app;        -- tenant-owned business data (RLS enforced)
CREATE SCHEMA audit;      -- tenant-owned audit trail and e-signatures (RLS, append-only)

COMMENT ON SCHEMA platform IS 'Cross-tenant platform data. No customer GxP data.';
COMMENT ON SCHEMA app      IS 'Tenant-owned business data. Every table has tenant_id and forced RLS.';
COMMENT ON SCHEMA audit    IS 'Tenant-owned, append-only audit trail and electronic signatures.';

-- -----------------------------------------------------------------------------
-- Tenant context. Set per transaction by the application:
--   SELECT set_config('app.tenant_id', '<uuid>', true);
-- Fails closed (raises) when no tenant is set.
-- -----------------------------------------------------------------------------
CREATE FUNCTION app.current_tenant() RETURNS uuid
    LANGUAGE plpgsql STABLE AS
$$
DECLARE
    v text := current_setting('app.tenant_id', true);
BEGIN
    IF v IS NULL OR v = '' THEN
        RAISE EXCEPTION 'ARQ-TENANT-001: tenant context not set'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN v::uuid;
END;
$$;
COMMENT ON FUNCTION app.current_tenant() IS
    'Returns the transaction tenant (app.tenant_id). Raises if not set, so RLS fails closed.';

-- -----------------------------------------------------------------------------
-- Audit completeness guard (audit-esignature.md §4).
-- Every write to a GxP table must happen inside an audit context opened by
-- AuditService.begin(), which sets app.audit_ctx for the transaction.
-- -----------------------------------------------------------------------------
CREATE FUNCTION app.require_audit_context() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    v text := current_setting('app.audit_ctx', true);
BEGIN
    IF v IS NULL OR v = '' THEN
        RAISE EXCEPTION 'ARQ-AUDIT-001: write to GxP table %.% without audit context',
            TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

-- -----------------------------------------------------------------------------
-- Append-only guard for audit tables (second line of defence after REVOKE).
-- -----------------------------------------------------------------------------
CREATE FUNCTION audit.prevent_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'ARQ-AUDIT-002: %.% is append-only (% not allowed)',
        TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

-- -----------------------------------------------------------------------------
-- Registry of GxP tables. V900 applies guards/grants to every registered table;
-- a CI linter checks every app table is either registered or explicitly non-GxP.
-- -----------------------------------------------------------------------------
CREATE TABLE platform.table_classification (
    table_name     text PRIMARY KEY,                  -- schema-qualified, e.g. 'app.study'
    classification text NOT NULL CHECK (classification IN ('GXP', 'SECURITY', 'NON_GXP', 'PLATFORM')),
    hard_delete    boolean NOT NULL DEFAULT false,    -- true only for non-GxP working tables
    note           text
);
COMMENT ON TABLE platform.table_classification IS
    'Data classification per table (audit-esignature.md §2). Drives guards and grants in V900.';
