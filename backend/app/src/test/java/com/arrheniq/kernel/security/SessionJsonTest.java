package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session attributes are stored as JSON with an allow-list of types (ADR 0018 follow-up; ASVS V5.5,
 * security-architecture T16): everything the app keeps in a session survives the round trip, and nothing else
 * is ever instantiated from stored bytes.
 */
@Requirement("URS-SEC-005")
class SessionJsonTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final SessionJson json = new SessionJson(SessionJsonTest.class.getClassLoader());

    /** Like a real login (ArrheniqOidcUserService): an authority and IdP user info. */
    private static OAuth2AuthenticationToken realisticAuthentication() {
        Instant now = Instant.now();
        // claim shapes as Keycloak sends them: an immutable list, a nested object, numbers, booleans, Instants
        OidcIdToken idToken = new OidcIdToken("token-value", now, now.plusSeconds(300), Map.of(
                "iss", SecurityTestSupport.ISSUER, "sub", "sub-1", "aud", List.of("arrheniq-app"),
                "preferred_username", "kim", "auth_time", now.getEpochSecond(), "email_verified", true,
                "iat", now, "realm_access", Map.of("roles", List.of("offline_access", "uma_authorization"))));
        OidcUserInfo info = new OidcUserInfo(Map.of("sub", "sub-1", "email", "kim@acme.test", "name", "Kim Analyst"));
        ArrheniqUser user = new ArrheniqUser(List.of(new SimpleGrantedAuthority("ARQ_USER")), idToken, info, TENANT,
                "acme", USER, "kim", "Kim Analyst");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "arrheniq");
    }

    @Test
    void theSignedInUserSurvivesTheRoundTrip() {
        OAuth2AuthenticationToken auth = realisticAuthentication();
        byte[] stored = json.write(new SecurityContextImpl(auth));
        String text = new String(stored, StandardCharsets.UTF_8);
        assertTrue(text.startsWith(SecurityContextImpl.class.getName() + "\n{") || text.startsWith(
                SecurityContextImpl.class.getName() + "\n["), text);

        Object read = json.read(stored);
        if (!(read instanceof SecurityContextImpl back)
                || !(back.getAuthentication() instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof ArrheniqUser user)) {
            throw new AssertionError("expected a security context with an ArrheniqUser, got " + read);
        }
        assertEquals(TENANT, user.tenantId());
        assertEquals("acme", user.tenantSlug());
        assertEquals(USER, user.userId());
        assertEquals("kim", user.username());
        assertEquals("Kim Analyst", user.displayName());
        assertEquals("sub-1", user.getSubject());
        assertEquals(TENANT + ":" + USER, user.getName());
        assertEquals(SecurityTestSupport.ISSUER, String.valueOf(user.getIdToken().getIssuer()));
        assertEquals("sub-1", user.getAttribute("sub"));
        assertEquals("kim@acme.test", user.getEmail());
        assertEquals(List.of("arrheniq-app"), user.getAudience());
        assertEquals(Boolean.TRUE, user.getEmailVerified());
        OidcIdToken original = ((ArrheniqUser) auth.getPrincipal()).getIdToken();
        assertEquals(original.getIssuedAt(), user.getIdToken().getIssuedAt());
        assertEquals(original.getExpiresAt(), user.getIdToken().getExpiresAt());
        assertEquals(original.getTokenValue(), user.getIdToken().getTokenValue());
        assertEquals(Map.of("roles", List.of("offline_access", "uma_authorization")), user.getClaim("realm_access"));
        assertTrue(user.getAuthorities().stream().anyMatch(a -> "ARQ_USER".equals(a.getAuthority())));
        assertEquals(auth.getAuthorizedClientRegistrationId(), token.getAuthorizedClientRegistrationId());
        assertTrue(token.isAuthenticated());
    }

    @Test
    void ourOwnAttributesSurviveTheRoundTrip() {
        Instant now = Instant.parse("2026-11-25T14:00:00Z");
        SigningIntent intent = new SigningIntent(UUID.randomUUID(), "PRODUCT", UUID.randomUUID(), 3);
        ReauthRequest pending = new ReauthRequest(TENANT, USER, ReauthRequest.Purpose.SIGN, now, "/products/1", intent);
        ReauthEvidence evidence = new ReauthEvidence(TENANT, USER, ReauthRequest.Purpose.SIGN, now, now.plusSeconds(2),
                intent);
        ReauthRequest unlock = new ReauthRequest(TENANT, USER, ReauthRequest.Purpose.UNLOCK, now, "/", null);

        for (Object value : new Object[] {pending, evidence, unlock, now, Duration.ofMinutes(30), Boolean.TRUE,
                "/studies/7"}) {
            assertEquals(value, json.read(json.write(value)), value.getClass().getSimpleName());
        }
    }

    @Test
    void typesThatAreNotAllowListedCanNeitherBeStoredNorRead() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> json.write(new Date()));
        assertThrows(IllegalArgumentException.class, () -> json.write(new HashMap<String, String>()));

        assertNull(json.read("java.util.HashMap\n{}".getBytes(StandardCharsets.UTF_8)));
        assertNull(json.read("no type line".getBytes(StandardCharsets.UTF_8)));
        assertNull(json.read((ReauthRequest.class.getName() + "\n{broken").getBytes(StandardCharsets.UTF_8)));

        // what the previous release stored (Java serialization) is ignored, never deserialized
        ByteArrayOutputStream javaSerialized = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(javaSerialized)) {
            out.writeObject("/old");
        }
        assertNull(json.read(javaSerialized.toByteArray()));
    }

    @Test
    void aNestedTypeOutsideTheAllowListIsRefused() {
        OAuth2AuthenticationToken auth = realisticAuthentication();
        String stored = new String(json.write(new SecurityContextImpl(auth)), StandardCharsets.UTF_8);
        assertTrue(stored.contains(ArrheniqUser.class.getName()), stored);
        // a tampered row names a classic deserialization gadget as the principal's class
        String tampered = stored.replace(ArrheniqUser.class.getName(), "javax.management.BadAttributeValueExpException");
        assertNull(json.read(tampered.getBytes(StandardCharsets.UTF_8)));
        assertFalse(stored.contains("rO0AB"), "no Java serialization inside the JSON");
    }
}
