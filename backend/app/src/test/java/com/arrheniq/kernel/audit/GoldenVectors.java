package com.arrheniq.kernel.audit;

/**
 * Reference values computed independently in PostgreSQL 16 (pgcrypto digest()), so the Java
 * implementation is checked against the database implementation used by platform.provision_tenant().
 * <pre>
 * SELECT encode(digest(convert_to('ARRHENIQ-GENESIS|11111111-1111-1111-1111-111111111111|2026-11-25T18:00:00.000001Z','UTF8')
 *        || '\\x00…00'::bytea (32 zero bytes), 'sha256'), 'hex');
 * </pre>
 */
final class GoldenVectors {

    static final String GENESIS_HASH_HEX = "00bb90384351c6e7c91bd0d9b07b7b91e30cf1d53ba1d67ef6c0db784e564e82";

    private GoldenVectors() { }
}
