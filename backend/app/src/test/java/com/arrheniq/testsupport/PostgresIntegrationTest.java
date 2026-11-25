package com.arrheniq.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Base for integration tests: one PostgreSQL 16 container per JVM (stopped by Testcontainers'
 * reaper), roles bootstrapped like db/bootstrap/00_roles_and_database.sql, Flyway as
 * arrheniq_owner, the application as arrheniq_app. Subclasses share one cached Spring context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationTest {

    protected static final String OWNER_PW = "it-owner";
    protected static final String APP_PW = "it-app";
    protected static final String PLATFORM_PW = "it-platform";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16")
            .withEnv("POSTGRES_PASSWORD", "postgres")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    private static boolean bootstrapped;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws SQLException {
        startOnce();
        registry.add("spring.datasource.url", () -> jdbcUrl("arrheniq"));
        registry.add("spring.datasource.username", () -> "arrheniq_app");
        registry.add("spring.datasource.password", () -> APP_PW);
        registry.add("spring.flyway.user", () -> "arrheniq_owner");
        registry.add("spring.flyway.password", () -> OWNER_PW);
        // OIDC client pointing at a non-routable IdP: tests never complete a real login, they only
        // check the redirects, and use a pre-built authentication for signed-in requests.
        String reg = "spring.security.oauth2.client.registration.arrheniq.";
        registry.add(reg + "provider", () -> "itidp");
        registry.add(reg + "client-id", () -> "it-client");
        registry.add(reg + "client-secret", () -> "it-secret");
        registry.add(reg + "authorization-grant-type", () -> "authorization_code");
        registry.add(reg + "redirect-uri", () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        registry.add(reg + "scope", () -> "openid,profile,email");
        String idp = "spring.security.oauth2.client.provider.itidp.";
        registry.add(idp + "authorization-uri", () -> IT_IDP + "/auth");
        registry.add(idp + "token-uri", () -> IT_IDP + "/token");
        registry.add(idp + "jwk-set-uri", () -> IT_IDP + "/jwks");
        registry.add(idp + "user-info-uri", () -> IT_IDP + "/userinfo");
        registry.add(idp + "user-name-attribute", () -> "sub");
    }

    /** Base URL of the fake IdP used in integration tests (never contacted). */
    protected static final String IT_IDP = "http://idp.invalid";

    private static synchronized void startOnce() throws SQLException {
        if (bootstrapped) {
            return;
        }
        POSTGRES.start();
        bootstrapRolesAndDatabase();
        bootstrapped = true;
    }

    protected static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl("arrheniq"), "arrheniq_owner", OWNER_PW);
    }

    protected static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl("arrheniq"), "arrheniq_app", APP_PW);
    }

    /** Provisions a tenant the way the platform console will (platform role, V11 function). */
    protected static UUID provisionTenant(String slug) throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(jdbcUrl("arrheniq"), "arrheniq_platform", PLATFORM_PW);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT platform.provision_tenant(?, ?, ?, ?, 'S1', 'Main site', 'America/New_York',"
                             + " 'PROFESSIONAL', 'it@arrheniq', false)")) {
            ps.setObject(1, tenant);
            ps.setString(2, slug + "-" + tenant.toString().substring(0, 8));
            ps.setString(3, slug + " Inc.");
            ps.setString(4, slug);
            ps.execute();
        }
        return tenant;
    }

    private static void bootstrapRolesAndDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl("postgres"), "postgres", "postgres");
             Statement s = c.createStatement()) {
            s.execute("CREATE ROLE arrheniq_owner LOGIN PASSWORD '" + OWNER_PW + "' NOSUPERUSER NOCREATEROLE NOBYPASSRLS");
            s.execute("CREATE ROLE arrheniq_app LOGIN PASSWORD '" + APP_PW + "' NOSUPERUSER NOCREATEROLE NOBYPASSRLS");
            s.execute("CREATE ROLE arrheniq_platform LOGIN PASSWORD '" + PLATFORM_PW
                    + "' NOSUPERUSER NOCREATEROLE NOBYPASSRLS");
            s.execute("CREATE DATABASE arrheniq OWNER arrheniq_owner ENCODING 'UTF8' TEMPLATE template0");
        }
        try (Connection c = DriverManager.getConnection(jdbcUrl("arrheniq"), "postgres", "postgres");
             Statement s = c.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            s.execute("REVOKE ALL ON DATABASE arrheniq FROM PUBLIC");
            s.execute("GRANT CONNECT ON DATABASE arrheniq TO arrheniq_app, arrheniq_platform");
            s.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        }
    }
}
