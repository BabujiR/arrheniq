package com.arrheniq;

import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the full application against a real PostgreSQL 16 (Testcontainers):
 * Flyway applies V1..V12 as arrheniq_owner, the app runs as arrheniq_app, then
 * the schema linter and deny-by-default security are checked.
 * Traces: URS-TEN-001, URS-SEC-001, URS-AUD-005; docs/data/schema.md.
 */
@Requirement({"URS-TEN-001", "URS-SEC-001", "URS-AUD-005"})
class ApplicationIT extends PostgresIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void allMigrationsAreApplied() throws SQLException {
        try (Connection c = ownerConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
            rs.next();
            assertEquals(12, rs.getInt(1), "expected V1..V12 applied");
        }
    }

    @Test
    void schemaLinterReportsNoViolations() throws SQLException, IOException {
        List<String> violations = new ArrayList<>();
        try (Connection c = ownerConnection();
             Statement s = c.createStatement()) {
            for (String query : lintQueries()) {
                try (ResultSet rs = s.executeQuery(query)) {
                    while (rs.next()) {
                        violations.add(rs.getString(1));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "schema lint violations: " + violations);
    }

    @Test
    void appRoleFailsClosedWithoutTenantContext() throws SQLException {
        try (Connection c = appConnection();
             Statement s = c.createStatement()) {
            SQLException e = assertThrows(SQLException.class, () -> s.executeQuery("SELECT count(*) FROM app.study"));
            assertTrue(e.getMessage().contains("ARQ-TENANT-001"), e.getMessage());
        }
    }

    @Test
    void healthIsPublicAndEverythingElseIsDenied() throws IOException, InterruptedException {
        String base = "http://localhost:" + environment.getRequiredProperty("local.server.port");
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("UP"), health.body());

        HttpResponse<String> api = http.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/anything")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertTrue(api.statusCode() == 401 || api.statusCode() == 403, "expected denial, got " + api.statusCode());
    }

    // ------------------------------------------------------------------ helpers

    /** SELECT statements of src/test/resources/db/schema_lint.sql (psql meta-commands stripped). */
    private static List<String> lintQueries() throws IOException {
        String sql = Files.readString(Path.of("src/test/resources/db/schema_lint.sql"), StandardCharsets.UTF_8);
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String t = line.strip();
            if (!t.startsWith("\\") && !t.startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        List<String> queries = new ArrayList<>();
        for (String stmt : cleaned.toString().split(";")) {
            if (stmt.strip().toUpperCase(java.util.Locale.ROOT).startsWith("SELECT")) {
                queries.add(stmt.strip());
            }
        }
        return queries;
    }
}
