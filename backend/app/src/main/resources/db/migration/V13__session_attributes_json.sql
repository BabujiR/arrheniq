-- =============================================================================
-- V13: session attributes are stored as JSON (S1.1; ADR 0018 follow-up, security-architecture T16).
-- Existing sessions hold Java-serialized attributes that the new reader refuses on purpose,
-- so they are removed: everyone signs in once more after this release.
-- =============================================================================
DELETE FROM platform.spring_session;   -- attributes are removed with them (ON DELETE CASCADE)

COMMENT ON COLUMN platform.spring_session_attributes.attribute_bytes IS
    'UTF-8 "<class name>\n<JSON>" written by SessionJson; only allow-listed classes are read (no Java serialization).';
