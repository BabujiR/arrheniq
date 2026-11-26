package com.arrheniq.kernel.security;

import com.arrheniq.identity.UserInvitationService;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real identity-provider round trip (ADR 0005/0018) against Keycloak in a container, driven by a plain
 * HTTP client acting as the browser: OIDC code flow with PKCE, first-login linking of an invited user by
 * verified e-mail, re-authentication for a signature ({@code prompt=login&max_age=0}, {@code auth_time} check),
 * and RP-initiated logout that also ends the IdP session. MockMvc tests ({@link SecurityIT}) cover the rules;
 * this test proves they hold with a real IdP.
 */
@Requirement({"URS-IAM-001", "URS-IAM-002", "URS-ESG-002", "URS-AUD-001"})
class KeycloakLoginIT extends PostgresIntegrationTest {

    /** Same version as infra/compose (local development); keep the two in step. */
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.8";
    private static final String REALM = "arrheniq-it";
    private static final String TENANT_SLUG = "kcit";
    private static final Pattern LOGIN_FORM = Pattern.compile("<form[^>]*\\bid=\"kc-form-login\"[^>]*>");
    private static final Pattern ACTION = Pattern.compile("\\baction=\"([^\"]+)\"");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(KEYCLOAK_IMAGE)
            .withCommand("start-dev", "--import-realm")
            .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak/arrheniq-it-realm.json"),
                    "/opt/keycloak/data/import/arrheniq-it-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration").forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static boolean userInvited;

    @DynamicPropertySource
    static void keycloak(DynamicPropertyRegistry registry) {
        if (!KEYCLOAK.isRunning()) {
            KEYCLOAK.start();
        }
        // a second registration "kc" next to the base class's offline one; the app logs in through "kc"
        registry.add("arrheniq.security.registration-id", () -> "kc");
        registry.add("arrheniq.security.fixed-tenant-slug", () -> TENANT_SLUG);
        registry.add("arrheniq.security.post-login-path", () -> "/api/v1/me");
        registry.add("arrheniq.security.signed-out-path", () -> "/auth/signed-out");   // backend page, no SPA here
        registry.add("server.servlet.session.cookie.secure", () -> "false");   // plain http in the test
        String reg = "spring.security.oauth2.client.registration.kc.";
        registry.add(reg + "provider", () -> "kc");
        registry.add(reg + "client-id", () -> "arrheniq-it");
        registry.add(reg + "client-secret", () -> "it-client-secret");
        registry.add(reg + "authorization-grant-type", () -> "authorization_code");
        registry.add(reg + "redirect-uri", () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        registry.add(reg + "scope", () -> "openid,profile,email");
        registry.add("spring.security.oauth2.client.provider.kc.issuer-uri", KeycloakLoginIT::issuer);
    }

    private static String issuer() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/" + REALM;
    }

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private UserInvitationService invitations;
    @Autowired
    private AuditService audit;
    @Autowired
    private PlatformTransactionManager txManager;

    private static UUID tenant;
    private Browser browser;

    @BeforeEach
    void setUp() throws SQLException {
        if (!userInvited) {
            tenant = provisionTenantWithSlug(TENANT_SLUG);
            TransactionTemplate tx = new TransactionTemplate(txManager);
            TenantContextHolder.callAs(TenantContext.system(tenant), () -> tx.execute(_ -> {
                audit.open("it.invite");
                return invitations.invite(AuditActor.system("it"), new UserInvitationService.Invitation(
                        "kcuser", "kcuser@kcit.test", "Kim Cloak",
                        List.of(new UserInvitationService.RoleAssignment("STABILITY_MANAGER", "S1"))));
            }));
            userInvited = true;
        }
        browser = new Browser();
    }

    @Test
    void loginReauthenticationAndLogoutWithARealIdentityProvider() throws Exception {
        // 1. login: app → Keycloak login form → credentials → app callback → returnTo
        HttpResponse<String> form = browser.get(app("/auth/login?returnTo=/api/v1/me"));
        assertTrue(form.uri().toString().startsWith(issuer()), "expected the Keycloak login page, got " + form.uri());
        String authUrl = browser.history.stream().filter(u -> u.contains("/protocol/openid-connect/auth?"))
                .findFirst().orElseThrow();
        assertTrue(authUrl.contains("code_challenge_method=S256"), "PKCE must be used: " + authUrl);
        assertFalse(authUrl.contains("prompt=login"), "a normal login must not force re-authentication");

        HttpResponse<String> me = browser.submitLogin(form, "kcuser", "it-password");
        assertEquals(200, me.statusCode(), me.uri() + " → " + me.body());
        assertEquals(app("/api/v1/me"), me.uri().toString());
        assertTrue(me.body().contains("\"username\":\"kcuser\""), me.body());
        assertTrue(me.body().contains("\"tenant\":\"" + TENANT_SLUG + "\""), me.body());

        // 2. re-authentication for a signature: Keycloak must ask for the password again
        browser.history.clear();
        HttpResponse<String> reauthForm = browser.get(app("/auth/reauth?purpose=SIGN&returnTo=/api/v1/me"
                + "&recordType=PRODUCT&recordId=" + UUID.randomUUID() + "&recordVersion=0"));
        String reauthUrl = browser.history.stream().filter(u -> u.contains("/protocol/openid-connect/auth?"))
                .findFirst().orElseThrow();
        assertTrue(reauthUrl.contains("prompt=login") && reauthUrl.contains("max_age=0"), reauthUrl);
        assertTrue(LOGIN_FORM.matcher(reauthForm.body()).find(), "Keycloak must show the login form again");
        HttpResponse<String> afterReauth = browser.submitLogin(reauthForm, "kcuser", "it-password");
        assertEquals(200, afterReauth.statusCode(), afterReauth.uri() + " → " + afterReauth.body());

        // 3. logout (POST + CSRF) ends the app session and the Keycloak session
        HttpResponse<String> loggedOut = browser.postEmpty(app("/auth/logout"),
                Map.of("X-XSRF-TOKEN", browser.cookie("XSRF-TOKEN")));
        assertTrue(browser.history.stream().anyMatch(u -> u.contains("/protocol/openid-connect/logout")),
                "logout must go through the IdP's end-session endpoint: " + browser.history);
        assertEquals(app("/auth/signed-out"), loggedOut.uri().toString().replaceAll("\\?.*$", ""));
        assertEquals(200, loggedOut.statusCode(), loggedOut.body());
        assertEquals(401, browser.get(app("/api/v1/me")).statusCode());
        HttpResponse<String> again = browser.get(app("/auth/login"));
        assertTrue(LOGIN_FORM.matcher(again.body()).find(), "after logout the IdP must ask for credentials again");

        // 4. everything above is in the tenant's audit trail
        List<String> actions = auditActions();
        assertTrue(actions.contains("LOGIN_SUCCESS"), actions.toString());
        assertTrue(actions.contains("REAUTH_SUCCESS"), actions.toString());
        assertTrue(actions.contains("LOGOUT"), actions.toString());
    }

    // ------------------------------------------------------------------ helpers

    private String app(String path) {
        return "http://localhost:" + port + path;
    }

    private static List<String> auditActions() throws SQLException {
        try (Connection c = ownerConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement t = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                t.setString(1, tenant.toString());
                t.execute();
            }
            try (PreparedStatement q = c.prepareStatement("SELECT action FROM audit.audit_event ORDER BY seq");
                 ResultSet rs = q.executeQuery()) {
                List<String> actions = new ArrayList<>();
                while (rs.next()) {
                    actions.add(rs.getString(1));
                }
                c.commit();
                return actions;
            }
        }
    }

    /** A minimal browser: keeps cookies, follows redirects itself and records every URL it visits. */
    private static final class Browser {

        private final CookieJar cookies = new CookieJar();
        private final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        private final List<String> history = new ArrayList<>();

        HttpResponse<String> get(String url) throws IOException, InterruptedException {
            return follow(HttpRequest.newBuilder(URI.create(url)).GET().build());
        }

        HttpResponse<String> postEmpty(String url, Map<String, String> headers) throws IOException, InterruptedException {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody());
            headers.forEach(b::header);
            return follow(b.build());
        }

        /** Fills in and submits Keycloak's login form, then follows the redirects back into the app. */
        HttpResponse<String> submitLogin(HttpResponse<String> page, String username, String password)
                throws IOException, InterruptedException {
            Matcher formTag = LOGIN_FORM.matcher(page.body());
            assertTrue(formTag.find(), "no Keycloak login form at " + page.uri());
            Matcher action = ACTION.matcher(formTag.group());
            assertTrue(action.find(), "login form has no action");
            URI target = page.uri().resolve(action.group(1).replace("&amp;", "&"));
            String body = Map.of("username", username, "password", password, "credentialId", "").entrySet().stream()
                    .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                    .collect(Collectors.joining("&"));
            return follow(HttpRequest.newBuilder(target)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }

        String cookie(String name) {
            return cookies.value(name).orElseThrow(() -> new AssertionError("no cookie " + name));
        }

        private HttpResponse<String> follow(HttpRequest first) throws IOException, InterruptedException {
            HttpRequest request = first;
            for (int hops = 0; hops < 15; hops++) {
                history.add(request.uri().toString());
                HttpResponse<String> r = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() / 100 != 3) {
                    return r;
                }
                String location = r.headers().firstValue("Location").orElseThrow();
                request = HttpRequest.newBuilder(r.uri().resolve(location)).GET().build();
            }
            throw new AssertionError("too many redirects: " + history);
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }

    /**
     * Host-only cookie jar. The JDK's CookieManager never sends Secure cookies over http, and Keycloak may
     * mark its login-session cookies Secure even on http; a real browser on localhost still sends them.
     */
    private static final class CookieJar extends CookieHandler {

        private final Map<String, HttpCookie> cookies = new LinkedHashMap<>();

        @Override
        public synchronized Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            String header = cookies.values().stream()
                    .filter(c -> uri.getHost().equalsIgnoreCase(c.getDomain()) && path.startsWith(c.getPath()))
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));
            return header.isEmpty() ? Map.of() : Map.of("Cookie", List.of(header));
        }

        @Override
        public synchronized void put(URI uri, Map<String, List<String>> responseHeaders) {
            responseHeaders.forEach((name, values) -> {
                if (name == null || !name.equalsIgnoreCase("Set-Cookie")) {
                    return;
                }
                for (String value : values) {
                    for (HttpCookie c : HttpCookie.parse(value)) {
                        c.setDomain(uri.getHost());
                        if (c.getPath() == null || c.getPath().isEmpty()) {
                            c.setPath("/");
                        }
                        String key = c.getName() + "|" + c.getDomain() + "|" + c.getPath();
                        boolean expired = c.getMaxAge() == 0 || (c.getMaxAge() < 0 && c.getMaxAge() != -1);
                        if (expired || c.getValue().isEmpty()) {
                            cookies.remove(key);
                        } else {
                            cookies.put(key, c);
                        }
                    }
                }
            });
        }

        synchronized java.util.Optional<String> value(String name) {
            return cookies.values().stream().filter(c -> c.getName().equals(name)).map(HttpCookie::getValue)
                    .findFirst();
        }
    }
}
