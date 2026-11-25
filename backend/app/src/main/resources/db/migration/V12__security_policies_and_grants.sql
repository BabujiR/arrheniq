-- =============================================================================
-- V12: row-level security, guards and grants for every table.
-- platform.apply_security() is idempotent. EVERY future migration that adds a
-- table must register it in platform.table_classification and end with:
--     SELECT platform.apply_security();
-- The CI schema linter (src/test/resources/db/schema_lint.sql) enforces this.
-- =============================================================================

CREATE FUNCTION platform.apply_security() RETURNS void
    LANGUAGE plpgsql AS
$$
DECLARE
    t record;
    c record;
    fq text;
BEGIN
    -- 1) Every table in app/audit must be tenant-scoped with forced RLS.
    FOR t IN
        SELECT n.nspname AS schema_name, cl.relname AS table_name
          FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
         WHERE cl.relkind = 'r' AND n.nspname IN ('app', 'audit')
    LOOP
        fq := format('%I.%I', t.schema_name, t.table_name);
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = t.schema_name AND table_name = t.table_name
                          AND column_name = 'tenant_id') THEN
            RAISE EXCEPTION 'ARQ-SCHEMA-001: table % has no tenant_id', fq;
        END IF;
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', fq);
        EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', fq);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %s', fq);
        EXECUTE format('CREATE POLICY tenant_isolation ON %s
                            USING (tenant_id = app.current_tenant())
                            WITH CHECK (tenant_id = app.current_tenant())', fq);
        EXECUTE format('REVOKE ALL ON %s FROM PUBLIC', fq);
    END LOOP;

    -- 2) Classification-driven grants and guards.
    FOR c IN SELECT * FROM platform.table_classification LOOP
        IF to_regclass(c.table_name) IS NULL THEN
            RAISE EXCEPTION 'ARQ-SCHEMA-002: classified table % does not exist', c.table_name;
        END IF;

        IF c.table_name LIKE 'audit.%' THEN
            -- append-only: INSERT + SELECT only (plus immutability triggers from V9)
            EXECUTE format('GRANT SELECT, INSERT ON %s TO arrheniq_app', c.table_name);
            EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON %s FROM arrheniq_app', c.table_name);

        ELSIF c.table_name LIKE 'app.%' THEN
            EXECUTE format('GRANT SELECT, INSERT, UPDATE ON %s TO arrheniq_app', c.table_name);
            IF c.hard_delete THEN
                EXECUTE format('GRANT DELETE ON %s TO arrheniq_app', c.table_name);
            ELSE
                EXECUTE format('REVOKE DELETE, TRUNCATE ON %s FROM arrheniq_app', c.table_name);  -- URS-AUD-005
            END IF;
            IF c.classification IN ('GXP', 'SECURITY') THEN
                EXECUTE format('DROP TRIGGER IF EXISTS require_audit_context ON %s', c.table_name);
                EXECUTE format('CREATE TRIGGER require_audit_context
                                    BEFORE INSERT OR UPDATE OR DELETE ON %s
                                    FOR EACH ROW EXECUTE FUNCTION app.require_audit_context()', c.table_name);
            END IF;

        ELSIF c.table_name LIKE 'platform.%' THEN
            EXECUTE format('REVOKE ALL ON %s FROM PUBLIC', c.table_name);
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %s TO arrheniq_platform', c.table_name);
            IF c.hard_delete THEN   -- jobs, sessions: runtime working tables of the app
                EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %s TO arrheniq_app', c.table_name);
            ELSIF c.table_name IN ('platform.tenant', 'platform.entitlement', 'platform.ref_dictionary',
                                   'platform.ref_dictionary_value', 'platform.ref_protocol_template',
                                   'platform.table_classification') THEN
                EXECUTE format('GRANT SELECT ON %s TO arrheniq_app', c.table_name);
            END IF;
        END IF;
    END LOOP;

    -- 3) Every table in app/audit must be classified.
    FOR t IN
        SELECT n.nspname || '.' || cl.relname AS fq
          FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace
         WHERE cl.relkind = 'r' AND n.nspname IN ('app', 'audit', 'platform')
           AND NOT EXISTS (SELECT 1 FROM platform.table_classification tc
                            WHERE tc.table_name = n.nspname || '.' || cl.relname)
    LOOP
        RAISE EXCEPTION 'ARQ-SCHEMA-003: table % is not classified', t.fq;
    END LOOP;
END;
$$;

GRANT USAGE ON SCHEMA app, audit, platform TO arrheniq_app, arrheniq_platform;
GRANT EXECUTE ON FUNCTION app.current_tenant() TO arrheniq_app, arrheniq_platform;
REVOKE ALL ON FUNCTION platform.apply_security() FROM PUBLIC;
GRANT SELECT ON platform.platform_audit_log TO arrheniq_platform;
GRANT INSERT ON platform.platform_audit_log TO arrheniq_platform;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA platform TO arrheniq_platform;

SELECT platform.apply_security();
