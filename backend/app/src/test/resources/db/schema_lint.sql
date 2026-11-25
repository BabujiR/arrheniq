-- =============================================================================
-- Schema linter (run in CI after migrations). Every query must return 0 rows.
-- Traces: URS-TEN-001, URS-AUD-001/005, multi-tenancy.md §7.3
-- =============================================================================
\set ON_ERROR_STOP 1
\pset footer off

\echo '[LINT-1] app/audit tables without forced RLS'
SELECT n.nspname || '.' || c.relname AS violation
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind = 'r' AND n.nspname IN ('app','audit')
   AND NOT (c.relrowsecurity AND c.relforcerowsecurity);

\echo '[LINT-2] app/audit tables without tenant_isolation policy'
SELECT n.nspname || '.' || c.relname AS violation
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind = 'r' AND n.nspname IN ('app','audit')
   AND NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid AND p.polname = 'tenant_isolation');

\echo '[LINT-3] app/audit primary keys not starting with tenant_id'
SELECT n.nspname || '.' || c.relname AS violation
  FROM pg_constraint k
  JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE k.contype = 'p' AND n.nspname IN ('app','audit')
   AND (SELECT a.attname FROM pg_attribute a WHERE a.attrelid = c.oid AND a.attnum = k.conkey[1]) <> 'tenant_id';

\echo '[LINT-4] foreign keys inside app/audit that do not include tenant_id'
SELECT k.conname || ' on ' || n.nspname || '.' || c.relname AS violation
  FROM pg_constraint k
  JOIN pg_class c ON c.oid = k.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE k.contype = 'f' AND n.nspname IN ('app','audit')
   AND NOT EXISTS (SELECT 1 FROM unnest(k.conkey) ck
                     JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = ck
                    WHERE a.attname = 'tenant_id');

\echo '[LINT-5] unclassified tables'
SELECT n.nspname || '.' || c.relname AS violation
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind = 'r' AND n.nspname IN ('app','audit','platform')
   AND NOT EXISTS (SELECT 1 FROM platform.table_classification t WHERE t.table_name = n.nspname || '.' || c.relname);

\echo '[LINT-6] GXP/SECURITY app tables without audit-context trigger'
SELECT t.table_name AS violation
  FROM platform.table_classification t
 WHERE t.table_name LIKE 'app.%' AND t.classification IN ('GXP','SECURITY')
   AND NOT EXISTS (SELECT 1 FROM pg_trigger g WHERE g.tgrelid = t.table_name::regclass
                      AND g.tgname = 'require_audit_context' AND NOT g.tgisinternal);

\echo '[LINT-7] GXP/SECURITY tables where arrheniq_app can DELETE or TRUNCATE'
SELECT t.table_name AS violation
  FROM platform.table_classification t
 WHERE t.classification IN ('GXP','SECURITY')
   AND (has_table_privilege('arrheniq_app', t.table_name, 'DELETE')
     OR has_table_privilege('arrheniq_app', t.table_name, 'TRUNCATE'));

\echo '[LINT-8] audit tables where arrheniq_app can UPDATE'
SELECT t.table_name AS violation
  FROM platform.table_classification t
 WHERE t.table_name LIKE 'audit.%' AND has_table_privilege('arrheniq_app', t.table_name, 'UPDATE');

\echo '[LINT-9] runtime roles with BYPASSRLS or superuser'
SELECT rolname AS violation FROM pg_roles
 WHERE rolname IN ('arrheniq_app','arrheniq_platform') AND (rolbypassrls OR rolsuper);

\echo '[LINT-10] arrheniq_app owns objects (must not)'
SELECT n.nspname || '.' || c.relname AS violation
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname IN ('app','audit','platform') AND pg_get_userbyid(c.relowner) = 'arrheniq_app';
