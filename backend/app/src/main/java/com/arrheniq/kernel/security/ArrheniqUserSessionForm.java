package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * How {@link ArrheniqUser} is stored in the session ({@link SessionJson}). Every component is a plain value; the
 * ID-token and user-info claims are kept as plain JSON <em>text</em>. So no claim value can name a Java type to be
 * instantiated, and claims of any shape survive (Keycloak and Cognito put lists and nested objects in tokens).
 * Read back, claims are plain JSON data (maps, lists, strings, numbers, booleans); Spring Security's claim
 * accessors convert them on use (e.g. an ISO time string to {@code Instant}, a string to {@code URL}).
 *
 * @param authorities authority names, space-separated
 */
record ArrheniqUserSessionForm(UUID tenantId, String tenantSlug, UUID userId, String username, String displayName,
                               String authorities, String idTokenValue, @Nullable Instant idTokenIssuedAt,
                               @Nullable Instant idTokenExpiresAt, String idTokenClaims,
                               @Nullable String userInfoClaims) {

    /** Plain JSON for claims: no type information, unlike the session mapper. */
    private static final JsonMapper PLAIN = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CLAIMS = new TypeReference<>() { };

    static ArrheniqUserSessionForm of(ArrheniqUser user) {
        OidcIdToken idToken = user.getIdToken();
        OidcUserInfo info = user.getUserInfo();
        String authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return new ArrheniqUserSessionForm(user.tenantId(), user.tenantSlug(), user.userId(), user.username(),
                user.displayName(), authorities, idToken.getTokenValue(), idToken.getIssuedAt(),
                idToken.getExpiresAt(), PLAIN.writeValueAsString(idToken.getClaims()),
                info == null ? null : PLAIN.writeValueAsString(info.getClaims()));
    }

    ArrheniqUser toUser() {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities.split(" "))
                .filter(a -> !a.isBlank())
                .map(SimpleGrantedAuthority::new)
                .toList();
        OidcIdToken idToken = new OidcIdToken(idTokenValue, idTokenIssuedAt, idTokenExpiresAt,
                PLAIN.readValue(idTokenClaims, CLAIMS));
        String infoJson = userInfoClaims;
        OidcUserInfo info = infoJson == null ? null : new OidcUserInfo(PLAIN.readValue(infoJson, CLAIMS));
        return new ArrheniqUser(granted, idToken, info, tenantId, tenantSlug, userId, username, displayName);
    }
}
