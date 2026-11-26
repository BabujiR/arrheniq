package com.arrheniq.kernel.security;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds signed-in MockMvc requests for integration tests in other modules: an {@link ArrheniqUser}
 * authentication, a session that passes {@link SessionContextFilter}, and re-authentication evidence.
 */
public final class SecurityTestSupport {

    public static final String ISSUER = "http://idp.invalid";

    private SecurityTestSupport() { }

    public static OAuth2AuthenticationToken authentication(UUID tenant, String tenantSlug, UUID userId, String subject,
                                                           String username, String displayName) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("it-token", now, now.plusSeconds(300), Map.of(
                "iss", ISSUER, "sub", subject, "aud", List.of("it-client"),
                "preferred_username", username, "auth_time", now.getEpochSecond()));
        ArrheniqUser user = new ArrheniqUser(List.of(), idToken, null, tenant, tenantSlug, userId, username,
                displayName);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "arrheniq");
    }

    public static MockHttpSession activeSession() {
        MockHttpSession session = new MockHttpSession();
        Instant now = Instant.now();
        session.setAttribute(SessionKeys.LOGIN_AT, now.minusSeconds(60));
        session.setAttribute(SessionKeys.LAST_ACTIVITY, now.minusSeconds(60));
        session.setAttribute(SessionKeys.IDLE_LOCK, Duration.ofMinutes(30));
        return session;
    }

    public static boolean hasSigningReauth(MockHttpSession session) {
        return session.getAttribute(SessionKeys.REAUTH_EVIDENCE) != null;
    }

    /**
     * What a successful {@code /auth/reauth?purpose=SIGN&recordType=…&recordId=…&recordVersion=…} round trip
     * leaves in the session: evidence that can sign only that record version.
     */
    public static void grantSigningReauth(MockHttpSession session, UUID tenant, UUID userId, String recordType,
                                          UUID recordId, long recordVersion) {
        Instant now = Instant.now();
        session.setAttribute(SessionKeys.REAUTH_EVIDENCE,
                new ReauthEvidence(tenant, userId, ReauthRequest.Purpose.SIGN, now.minusSeconds(5), now,
                        new SigningIntent(UUID.randomUUID(), recordType, recordId, recordVersion)));
    }
}
