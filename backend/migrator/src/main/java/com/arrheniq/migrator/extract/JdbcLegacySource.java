package com.arrheniq.migrator.extract;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JDBC implementation (Oracle driver on the classpath at runtime). Opens a READ ONLY transaction
 * so the extraction sees one consistent snapshot and can never modify the source.
 */
public final class JdbcLegacySource implements LegacySource {

    private final Connection connection;

    public JdbcLegacySource(String jdbcUrl, String user, String password) throws Exception {
        this.connection = DriverManager.getConnection(jdbcUrl, user, password);
        this.connection.setAutoCommit(false);
        this.connection.setReadOnly(true);
        try (Statement s = connection.createStatement()) {
            s.execute("SET TRANSACTION READ ONLY");
        }
    }

    @Override
    public long stream(String table, Consumer<LegacyRow> consumer) throws Exception {
        requireWhitelisted(table);
        long n = 0;
        try (Statement s = connection.createStatement()) {
            s.setFetchSize(1_000);
            // Table name comes from the LegacyTables whitelist only; no user input reaches this SQL.
            try (ResultSet rs = s.executeQuery("SELECT * FROM " + table)) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        String col = md.getColumnName(i).toUpperCase(Locale.ROOT);
                        if (!LegacyTables.NEVER_EXTRACT_COLUMNS.contains(col)) {
                            row.put(col, rs.getObject(i));
                        }
                    }
                    consumer.accept(new LegacyRow(table, row));
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public long count(String table) throws Exception {
        requireWhitelisted(table);
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void requireWhitelisted(String table) {
        if (!LegacyTables.isKnown(table)) {
            throw new IllegalArgumentException("Table not in migration whitelist: " + table);
        }
    }

    @Override
    public void close() throws java.sql.SQLException {
        connection.rollback();
        connection.close();
    }
}
