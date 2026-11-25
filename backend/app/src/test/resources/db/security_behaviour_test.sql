-- =============================================================================
-- Database security behaviour tests (run in CI against a fresh migrated DB).
-- Part 1 runs as arrheniq_platform, part 2 as arrheniq_app (see run_db_tests.sh).
-- Each DO block raises 'TEST FAILED' on unexpected behaviour.
-- Traces: URS-TEN-001, URS-AUD-001/003/005, URS-SEC-001, multi-tenancy.md §7,
--         audit-esignature.md §10
-- =============================================================================
\set ON_ERROR_STOP 1

\if :{?part}
\else
  \echo 'usage: psql -v part=platform|app -f security_behaviour_test.sql'
  \quit
\endif

\if :part_is_platform
-- ---------------------------------------------------------------- PART 1
\echo '[T01] provision two tenants'
SELECT platform.provision_tenant('11111111-1111-1111-1111-111111111111','tenant-a','Tenant A Inc.','Tenant A',
                                 'NJ','New Jersey QC','America/New_York','PROFESSIONAL','ci@arrheniq',false);
SELECT platform.provision_tenant('22222222-2222-2222-2222-222222222222','tenant-b','Tenant B LLC','Tenant B',
                                 'NC','North Carolina QC','America/New_York','STARTER','ci@arrheniq',true);

\echo '[T02] platform role cannot read tenant data directly (RLS, no context)'
DO $$ BEGIN
  PERFORM count(*) FROM app.site;
  RAISE EXCEPTION 'TEST FAILED: platform role read app.site without tenant context';
EXCEPTION WHEN insufficient_privilege THEN NULL;   -- ARQ-TENANT-001 or permission denied
END $$;
\echo 'platform part OK'

\else
-- ---------------------------------------------------------------- PART 2 (arrheniq_app)
\echo '[T10] no tenant context -> every read fails closed'
DO $$ BEGIN
  PERFORM count(*) FROM app.study;
  RAISE EXCEPTION 'TEST FAILED: read without tenant context succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;

\echo '[T11] tenant A sees only its own data'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
DO $$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM app.site;               IF n <> 1 THEN RAISE EXCEPTION 'TEST FAILED: A sees % sites', n; END IF;
  SELECT count(DISTINCT tenant_id) INTO n FROM app.dictionary_value;
                                                      IF n <> 1 THEN RAISE EXCEPTION 'TEST FAILED: A sees % tenants in dictionaries', n; END IF;
  SELECT count(*) INTO n FROM app.role WHERE preset;  IF n <> 8 THEN RAISE EXCEPTION 'TEST FAILED: expected 8 preset roles, got %', n; END IF;
  SELECT count(*) INTO n FROM audit.audit_event;      IF n <> 1 THEN RAISE EXCEPTION 'TEST FAILED: expected genesis event only, got %', n; END IF;
  SELECT count(*) INTO n FROM app.dictionary_value v JOIN app.dictionary d ON d.tenant_id = v.tenant_id AND d.id = v.dictionary_id
   WHERE d.code = 'STORAGE_CONDITION';                IF n < 10 THEN RAISE EXCEPTION 'TEST FAILED: ICH conditions not seeded (%)', n; END IF;
END $$;
COMMIT;

\echo '[T12] write without audit context is rejected (ARQ-AUDIT-001)'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
DO $$ BEGIN
  INSERT INTO app.site (tenant_id, id, code, name) VALUES ('11111111-1111-1111-1111-111111111111', gen_random_uuid(), 'X1', 'No audit');
  RAISE EXCEPTION 'TEST FAILED: GxP insert without audit context succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
COMMIT;

\echo '[T13] cannot write rows for another tenant (RLS WITH CHECK)'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.audit_ctx','test', true);
DO $$ BEGIN
  INSERT INTO app.site (tenant_id, id, code, name) VALUES ('22222222-2222-2222-2222-222222222222', gen_random_uuid(), 'X2', 'Cross tenant');
  RAISE EXCEPTION 'TEST FAILED: cross-tenant insert succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;   -- new row violates row-level security policy
END $$;
COMMIT;

\echo '[T14] cannot reference another tenant''s row (composite FK)'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.audit_ctx','test', true);
DO $$ DECLARE b_site uuid; BEGIN
  -- B's site id is invisible to A; use a random id to emulate a guessed id
  INSERT INTO app.product (tenant_id, id, site_id, code, name, product_type, expiration_value, expiration_unit)
  VALUES ('11111111-1111-1111-1111-111111111111', gen_random_uuid(), gen_random_uuid(), 'P-X', 'X', 'DRUG_PRODUCT', 24, 'MONTH');
  RAISE EXCEPTION 'TEST FAILED: FK to non-visible site accepted';
EXCEPTION WHEN foreign_key_violation THEN NULL;
END $$;
COMMIT;

\echo '[T15] valid GxP write inside audit context succeeds'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.audit_ctx','test', true);
INSERT INTO app.product (tenant_id, id, site_id, code, name, product_type, expiration_value, expiration_unit)
SELECT tenant_id, '33333333-3333-3333-3333-333333333333', id, 'P-001', 'Metformin HCl ER 500 mg', 'DRUG_PRODUCT', 24, 'MONTH'
  FROM app.site LIMIT 1;
COMMIT;

\echo '[T16] hard delete of GxP rows is not permitted (URS-AUD-005)'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.audit_ctx','test', true);
DO $$ BEGIN
  DELETE FROM app.product WHERE id = '33333333-3333-3333-3333-333333333333';
  RAISE EXCEPTION 'TEST FAILED: hard delete succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
COMMIT;

\echo '[T17] logical delete requires a reason'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
SELECT set_config('app.audit_ctx','test', true);
DO $$ BEGIN
  UPDATE app.product SET deleted_at = now() WHERE id = '33333333-3333-3333-3333-333333333333';
  RAISE EXCEPTION 'TEST FAILED: logical delete without reason succeeded';
EXCEPTION WHEN check_violation THEN NULL;
END $$;
COMMIT;

\echo '[T18] audit trail is append-only'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
DO $$ BEGIN
  UPDATE audit.audit_event SET reason = 'tampered' WHERE seq = 1;
  RAISE EXCEPTION 'TEST FAILED: audit update succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
DO $$ BEGIN
  DELETE FROM audit.audit_event WHERE seq = 1;
  RAISE EXCEPTION 'TEST FAILED: audit delete succeeded';
EXCEPTION WHEN insufficient_privilege THEN NULL;
END $$;
COMMIT;

\echo '[T19] audit chain: out-of-order append rejected, correct append accepted'
BEGIN;
SELECT set_config('app.tenant_id','11111111-1111-1111-1111-111111111111', true);
DO $$ DECLARE tail bytea; BEGIN
  BEGIN
    INSERT INTO audit.audit_event (tenant_id, seq, id, actor_name, actor_type, action, record_type, correlation_id, prev_hash, hash)
    VALUES ('11111111-1111-1111-1111-111111111111', 5, gen_random_uuid(), 'ci', 'SYSTEM', 'TEST', 'TEST', 'ci',
            '\x0000000000000000000000000000000000000000000000000000000000000000', sha256('x'::bytea));
    RAISE EXCEPTION 'TEST FAILED: gap in chain accepted';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM NOT LIKE 'ARQ-AUDIT-004%' THEN RAISE; END IF;
  END;
  SELECT hash INTO tail FROM audit.audit_event WHERE seq = 1;
  INSERT INTO audit.audit_event (tenant_id, seq, id, actor_name, actor_type, action, record_type, record_id,
                                 changes, correlation_id, prev_hash, hash)
  VALUES ('11111111-1111-1111-1111-111111111111', 2, gen_random_uuid(), 'ci', 'SYSTEM', 'CREATE', 'PRODUCT',
          '33333333-3333-3333-3333-333333333333', '[{"field":"code","old":null,"new":"P-001"}]', 'ci',
          tail, sha256('event-2'::bytea || tail));
END $$;
COMMIT;

\echo '[T20] tenant B cannot see tenant A product or audit events'
BEGIN;
SELECT set_config('app.tenant_id','22222222-2222-2222-2222-222222222222', true);
DO $$ DECLARE n int; BEGIN
  SELECT count(*) INTO n FROM app.product;          IF n <> 0 THEN RAISE EXCEPTION 'TEST FAILED: B sees A products'; END IF;
  SELECT count(*) INTO n FROM audit.audit_event;    IF n <> 1 THEN RAISE EXCEPTION 'TEST FAILED: B sees % audit events', n; END IF;
END $$;
COMMIT;

\echo '[T21] platform tables: app can read reference catalog but not modify it'
DO $$ BEGIN
  PERFORM 1 FROM platform.ref_dictionary LIMIT 1;
  BEGIN
    INSERT INTO platform.ref_dictionary (code, name, category) VALUES ('HACK','x','SYSTEM');
    RAISE EXCEPTION 'TEST FAILED: app modified reference catalog';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END $$;

\echo 'app part OK'
\endif
