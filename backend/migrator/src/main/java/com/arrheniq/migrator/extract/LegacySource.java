package com.arrheniq.migrator.extract;

import java.util.Map;
import java.util.function.Consumer;

/** Read-only access to a restored legacy eSTAR database (migration-design.md §2, stage 1). */
public interface LegacySource extends AutoCloseable {

    /** One legacy row: upper-case column name → value (String, BigDecimal, java.time types, byte[] or null). */
    record LegacyRow(String table, Map<String, Object> columns) { }

    /** Streams all rows of a whitelisted table to the consumer; returns the row count. */
    long stream(String table, Consumer<LegacyRow> consumer) throws Exception;

    /** Row count of a whitelisted table (for reconciliation, including excluded tables). */
    long count(String table) throws Exception;

    @Override
    void close() throws java.sql.SQLException;
}
