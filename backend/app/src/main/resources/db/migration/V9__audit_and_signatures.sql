-- =============================================================================
-- V9: audit trail, electronic signatures, anchors (audit-esignature.md; ADR 0004, 0016)
-- =============================================================================

CREATE TABLE audit.audit_event (
    tenant_id        uuid        NOT NULL,
    seq              bigint      NOT NULL CHECK (seq >= 1),   -- per-tenant gapless chain position
    id               uuid        NOT NULL,
    occurred_at      timestamptz NOT NULL DEFAULT now(),      -- DB clock, UTC
    site_id          uuid,
    actor_user_id    uuid,
    actor_name       text        NOT NULL,
    actor_type       text        NOT NULL CHECK (actor_type IN ('USER','API_KEY','SYSTEM','MIGRATION','PLATFORM')),
    action           text        NOT NULL,
    record_type      text        NOT NULL,
    record_id        uuid,
    record_version   bigint,
    business_key     text,
    changes          jsonb       NOT NULL DEFAULT '[]'::jsonb,  -- [{field, old, new}]
    reason           text,
    reason_code      text,
    correlation_id   text        NOT NULL,
    client_info      jsonb,
    legacy           boolean     NOT NULL DEFAULT false,         -- migrated eSTAR history (URS-MIG-003)
    prev_hash        bytea       NOT NULL CHECK (octet_length(prev_hash) = 32),
    hash             bytea       NOT NULL CHECK (octet_length(hash) = 32),
    PRIMARY KEY (tenant_id, seq),
    UNIQUE (tenant_id, id)
);
CREATE INDEX audit_event_record_idx ON audit.audit_event (tenant_id, record_type, record_id, seq);
CREATE INDEX audit_event_actor_idx  ON audit.audit_event (tenant_id, actor_user_id, occurred_at);
CREATE INDEX audit_event_time_idx   ON audit.audit_event (tenant_id, occurred_at);
COMMENT ON TABLE audit.audit_event IS
 'Append-only, hash-chained audit trail. hash = SHA-256(canonical(event) || prev_hash). URS-AUD-001..003.';

-- The chain must be contiguous: each new event must follow the current tail.
CREATE FUNCTION audit.check_chain_append() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    tail_seq  bigint;
    tail_hash bytea;
BEGIN
    SELECT seq, hash INTO tail_seq, tail_hash
      FROM audit.audit_event
     WHERE tenant_id = NEW.tenant_id
     ORDER BY seq DESC
     LIMIT 1;
    IF tail_seq IS NULL THEN
        IF NEW.seq <> 1 OR NEW.prev_hash <> '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea THEN
            RAISE EXCEPTION 'ARQ-AUDIT-003: first event of tenant % must have seq 1 and zero prev_hash', NEW.tenant_id;
        END IF;
    ELSIF NEW.seq <> tail_seq + 1 OR NEW.prev_hash <> tail_hash THEN
        RAISE EXCEPTION 'ARQ-AUDIT-004: chain break for tenant % (expected seq %, got %)',
            NEW.tenant_id, tail_seq + 1, NEW.seq;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER audit_event_chain BEFORE INSERT ON audit.audit_event
    FOR EACH ROW EXECUTE FUNCTION audit.check_chain_append();
CREATE TRIGGER audit_event_immutable BEFORE UPDATE OR DELETE ON audit.audit_event
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_modification();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit.audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit.prevent_modification();

CREATE TABLE audit.e_signature (
    tenant_id        uuid        NOT NULL,
    id               uuid        NOT NULL,
    record_type      text        NOT NULL,
    record_id        uuid        NOT NULL,
    record_version   bigint      NOT NULL,
    content_hash     bytea       NOT NULL CHECK (octet_length(content_hash) = 32),
    meaning          text        NOT NULL,
    signer_user_id   uuid        NOT NULL,
    signer_name      text        NOT NULL,
    signed_at        timestamptz NOT NULL DEFAULT now(),
    auth_time        timestamptz NOT NULL,
    idp_issuer       text        NOT NULL,
    idp_acr          text,
    signing_intent_id uuid,
    comment          text,
    legacy           boolean     NOT NULL DEFAULT false,      -- migrated eSTAR approvals (no Part 11 e-sig)
    PRIMARY KEY (tenant_id, id),
    CHECK (legacy OR auth_time <= signed_at)
);
CREATE INDEX e_signature_record_idx ON audit.e_signature (tenant_id, record_type, record_id);
COMMENT ON TABLE audit.e_signature IS 'Electronic signatures bound to record version + content hash (URS-ESG-001..003).';

-- Signature status is derived, never updated in place: a status change is a new row.
CREATE TABLE audit.e_signature_status (
    tenant_id        uuid        NOT NULL,
    id               uuid        NOT NULL,
    signature_id     uuid        NOT NULL,
    status           text        NOT NULL CHECK (status IN ('SUPERSEDED','REVOKED')),
    reason           text        NOT NULL,
    occurred_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, signature_id) REFERENCES audit.e_signature (tenant_id, id)
);

CREATE TABLE audit.audit_anchor (
    tenant_id        uuid        NOT NULL,
    anchor_date      date        NOT NULL,
    last_seq         bigint      NOT NULL,
    last_hash        bytea       NOT NULL,
    object_key       text        NOT NULL,           -- copy in S3 Object Lock (compliance mode)
    created_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, anchor_date)
);
COMMENT ON TABLE audit.audit_anchor IS 'Daily chain anchors, also written to WORM storage (audit-esignature.md §8).';

CREATE TRIGGER e_signature_immutable BEFORE UPDATE OR DELETE ON audit.e_signature
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_modification();
CREATE TRIGGER e_signature_status_immutable BEFORE UPDATE OR DELETE ON audit.e_signature_status
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_modification();
CREATE TRIGGER audit_anchor_immutable BEFORE UPDATE OR DELETE ON audit.audit_anchor
    FOR EACH ROW EXECUTE FUNCTION audit.prevent_modification();

INSERT INTO platform.table_classification (table_name, classification) VALUES
 ('audit.audit_event','GXP'), ('audit.e_signature','GXP'), ('audit.e_signature_status','GXP'), ('audit.audit_anchor','GXP');
