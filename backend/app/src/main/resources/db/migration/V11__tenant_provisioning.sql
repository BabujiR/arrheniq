-- =============================================================================
-- V11: tenant provisioning (multi-tenancy.md §5). Called by operator tooling
-- as arrheniq_platform:
--   SELECT platform.provision_tenant('<uuid>', 'acme', 'Acme Pharma Inc.', 'Acme',
--          'NJ', 'New Jersey QC', 'America/New_York', 'PROFESSIONAL', 'ops@arrheniq', false);
-- Creates: tenant, entitlement, first site, preset roles + permissions,
-- dictionaries copied from the catalog, default settings, fixed label templates,
-- sequences, and the genesis audit event (seq 1) of the tenant's hash chain.
-- =============================================================================

CREATE FUNCTION platform.provision_tenant(
    p_tenant_id    uuid,
    p_slug         text,
    p_legal_name   text,
    p_display_name text,
    p_site_code    text,
    p_site_name    text,
    p_time_zone    text,
    p_edition      text,
    p_operator     text,
    p_sandbox      boolean DEFAULT false
) RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog, public
AS
$$
DECLARE
    v_site_id   uuid := gen_random_uuid();
    v_now       timestamptz := now();
    v_role      record;
    v_role_id   uuid;
    v_perm      text;
    v_dict      record;
    v_dict_id   uuid;
    v_genesis   bytea;
    v_zero      bytea := '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea;
    v_sites     int;
    v_studies   int;
BEGIN
    IF p_edition NOT IN ('STARTER','PROFESSIONAL','ENTERPRISE') THEN
        RAISE EXCEPTION 'ARQ-PROV-001: unknown edition %', p_edition;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_timezone_names WHERE name = p_time_zone) THEN
        RAISE EXCEPTION 'ARQ-PROV-002: unknown time zone %', p_time_zone;
    END IF;

    INSERT INTO platform.tenant (id, slug, legal_name, display_name, status, sandbox)
    VALUES (p_tenant_id, p_slug, p_legal_name, p_display_name, 'PROVISIONING', p_sandbox);

    SELECT CASE p_edition WHEN 'STARTER' THEN 1 WHEN 'PROFESSIONAL' THEN 1 ELSE 3 END,
           CASE p_edition WHEN 'STARTER' THEN 75 WHEN 'PROFESSIONAL' THEN 300 ELSE 1000 END
      INTO v_sites, v_studies;
    INSERT INTO platform.entitlement (tenant_id, edition, sites_included, studies_included, valid_from)
    VALUES (p_tenant_id, p_edition, v_sites, v_studies, current_date);

    -- tenant + audit context for this transaction
    PERFORM set_config('app.tenant_id', p_tenant_id::text, true);
    PERFORM set_config('app.audit_ctx', 'PROVISIONING', true);

    -- genesis audit event: hash = SHA-256('ARRHENIQ-GENESIS|' || tenant || '|' || occurred_at) || zero)
    v_genesis := digest(convert_to('ARRHENIQ-GENESIS|' || p_tenant_id::text || '|' ||
                                   to_char(v_now AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), 'UTF8') || v_zero,
                        'sha256');
    INSERT INTO audit.audit_event (tenant_id, seq, id, occurred_at, actor_name, actor_type, action,
                                   record_type, record_id, business_key, changes, correlation_id, prev_hash, hash)
    VALUES (p_tenant_id, 1, gen_random_uuid(), v_now, p_operator, 'PLATFORM', 'TENANT_PROVISIONED',
            'TENANT', p_tenant_id, p_slug,
            jsonb_build_array(jsonb_build_object('field','edition','old',NULL,'new',p_edition),
                              jsonb_build_object('field','sandbox','old',NULL,'new',p_sandbox)),
            'provision:' || p_tenant_id::text, v_zero, v_genesis);

    -- first site
    INSERT INTO app.site (tenant_id, id, code, name, time_zone)
    VALUES (p_tenant_id, v_site_id, p_site_code, p_site_name, p_time_zone);

    -- preset roles (URS-IAM-006). Tenant admin has no GxP approval rights (SoD).
    FOR v_role IN
        SELECT * FROM (VALUES
          ('TENANT_ADMIN','Tenant administrator', ARRAY[
              'tenant.admin','site.manage','user.manage','role.manage','config.manage','apikey.manage',
              'audit.view','audit.export','masterdata.view','study.view','schedule.view','result.view','export.data']),
          ('STABILITY_MANAGER','Stability manager', ARRAY[
              'masterdata.view','test.manage','product.manage','spec.manage','dictionary.manage','protocol.manage',
              'study.view','study.create','study.terminate','study.backentry.attest','attachment.manage',
              'schedule.view','schedule.change_date','pull.perform','pull.extra','pull.reverse','inventory.destroy',
              'label.print','label.reprint','result.view','investigation.manage','evaluation.run',
              'report.generate','audit.view']),
          ('ANALYST','QC analyst', ARRAY[
              'masterdata.view','study.view','schedule.view','pull.perform','label.print',
              'result.view','result.enter','result.import','report.generate']),
          ('REVIEWER_L1','Reviewer (level 1)', ARRAY[
              'masterdata.view','study.view','schedule.view','result.view','result.approve.l1',
              'investigation.manage','report.generate','audit.view']),
          ('APPROVER_QA','QA approver (final)', ARRAY[
              'masterdata.view','product.approve','spec.approve','protocol.approve','study.view','study.approve',
              'schedule.view','result.view','result.approve.final','result.edit_approved','investigation.manage',
              'evaluation.sign','report.generate','report.sign','audit.view','audit.export']),
          ('STATISTICIAN','Statistician / CMC', ARRAY[
              'masterdata.view','study.view','schedule.view','result.view','evaluation.run','evaluation.sign',
              'report.generate']),
          ('READ_ONLY','Read only', ARRAY[
              'masterdata.view','study.view','schedule.view','result.view']),
          ('CLIENT_VIEWER','CRO client viewer', ARRAY[
              'study.view','result.view'])
        ) AS r(code, name, perms)
    LOOP
        v_role_id := gen_random_uuid();
        INSERT INTO app.role (tenant_id, id, code, name, preset) VALUES (p_tenant_id, v_role_id, v_role.code, v_role.name, true);
        FOREACH v_perm IN ARRAY v_role.perms LOOP
            INSERT INTO app.role_permission (tenant_id, id, role_id, permission)
            VALUES (p_tenant_id, gen_random_uuid(), v_role_id, v_perm);
        END LOOP;
    END LOOP;

    -- dictionaries from the catalog (URS-MD-006/007)
    FOR v_dict IN SELECT * FROM platform.ref_dictionary LOOP
        v_dict_id := gen_random_uuid();
        INSERT INTO app.dictionary (tenant_id, id, code, name, category, system_managed)
        VALUES (p_tenant_id, v_dict_id, v_dict.code, v_dict.name, v_dict.category, NOT v_dict.allow_tenant_values);
        INSERT INTO app.dictionary_value (tenant_id, id, dictionary_id, code, label, description, sort_order, attributes)
        SELECT p_tenant_id, gen_random_uuid(), v_dict_id, v.code, v.label, v.description, v.sort_order, v.attributes
          FROM platform.ref_dictionary_value v
         WHERE v.dictionary_code = v_dict.code;
    END LOOP;

    -- default settings: equivalents of the 28 legacy EA_APPL_PARAMETER rows (legacy-mapping.md §6)
    INSERT INTO app.tenant_setting (tenant_id, id, setting_key, value)
    SELECT p_tenant_id, gen_random_uuid(), k, v FROM (VALUES
        ('company.name',                         to_jsonb(p_legal_name)),
        ('session.idle_lock_minutes',            '30'::jsonb),
        ('session.logoff_minutes',               '60'::jsonb),
        ('approval.product',                     '"SINGLE"'::jsonb),
        ('approval.specification',               '"SINGLE"'::jsonb),
        ('approval.protocol',                    '"SINGLE"'::jsonb),
        ('approval.study',                       '"SINGLE"'::jsonb),
        ('approval.results',                     '"DOUBLE"'::jsonb),
        ('approval.notify_on_request',           'true'::jsonb),
        ('results.segregation_of_duties',        'true'::jsonb),
        ('results.reference_required',           'false'::jsonb),
        ('results.qualifier_policy',             '"EXCLUDE_AND_FLAG"'::jsonb),
        ('signature.series_window_seconds',      '0'::jsonb),
        ('scheduling.saturday_rule',             '"LEAVE"'::jsonb),
        ('scheduling.sunday_rule',               '"MOVE_BEFORE"'::jsonb),
        ('scheduling.holiday_rule',              '"MOVE_AFTER"'::jsonb),
        ('scheduling.pull_window_days',          '3'::jsonb),
        ('inventory.extras_allowed',             'false'::jsonb),
        ('inventory.partial_pull_allowed',       'false'::jsonb),
        ('labels.required_for_study_approval',   'false'::jsonb),
        ('notify.days_before_pull',              '1'::jsonb),
        ('notify.days_after_pull_not_started',   '1'::jsonb),
        ('notify.days_after_pull_not_completed', '1'::jsonb),
        ('reports.late_days_ok',                 '0'::jsonb),
        ('study.code_pattern',                   '"YYnnnn"'::jsonb)
    ) AS s(k, v);

    -- fixed label templates (MVP; URS-LBL-002/003)
    INSERT INTO app.label_template (tenant_id, id, code, name, layout, fields, barcode_symbology) VALUES
     (p_tenant_id, gen_random_uuid(), 'STD_2D', 'Standard 2D (sample)', 'SMALL_2D',
      '["product_name","study_code","lot_number","condition","timepoint_label","scheduled_date","fill"]'::jsonb, 'DATAMATRIX'),
     (p_tenant_id, gen_random_uuid(), 'STD_1D', 'Standard 1D (sample)', 'STANDARD_1D',
      '["product_name","study_code","lot_number","condition","timepoint_label","scheduled_date"]'::jsonb, 'CODE128'),
     (p_tenant_id, gen_random_uuid(), 'LARGE_2D', 'Large 2D (tray/box)', 'LARGE_2D',
      '["product_code","product_name","study_code","lot_number","condition","container","timepoint_label","scheduled_date","specification"]'::jsonb, 'DATAMATRIX');

    INSERT INTO app.tenant_sequence (tenant_id, name, scope_key, next_value) VALUES (p_tenant_id, 'DOCUMENT_NUMBER', '', 1);

    UPDATE platform.tenant SET status = 'ACTIVE' WHERE id = p_tenant_id;
    INSERT INTO platform.platform_audit_log (operator, action, tenant_id, details)
    VALUES (p_operator, 'TENANT_PROVISIONED', p_tenant_id,
            jsonb_build_object('slug', p_slug, 'edition', p_edition, 'sandbox', p_sandbox, 'site', p_site_code));
END;
$$;

REVOKE ALL ON FUNCTION platform.provision_tenant(uuid,text,text,text,text,text,text,text,text,boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION platform.provision_tenant(uuid,text,text,text,text,text,text,text,text,boolean) TO arrheniq_platform;
