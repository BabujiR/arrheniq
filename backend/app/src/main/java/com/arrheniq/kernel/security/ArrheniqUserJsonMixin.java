package com.arrheniq.kernel.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.util.Collection;
import java.util.UUID;

/**
 * JSON shape of {@link ArrheniqUser} in the session ({@link SessionJson}): its fields, plus the ID token,
 * user info and authorities of the OIDC user it extends (same approach as Spring Security's own
 * {@code DefaultOidcUserMixin}). {@code attributes} are derived from the tokens and not stored.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(value = {"attributes", "nameAttributeKey"}, ignoreUnknown = true)
abstract class ArrheniqUserJsonMixin {

    @JsonCreator
    ArrheniqUserJsonMixin(@JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities,
                          @JsonProperty("idToken") OidcIdToken idToken,
                          @JsonProperty("userInfo") @Nullable OidcUserInfo userInfo,
                          @JsonProperty("tenantId") UUID tenantId,
                          @JsonProperty("tenantSlug") String tenantSlug,
                          @JsonProperty("userId") UUID userId,
                          @JsonProperty("username") String username,
                          @JsonProperty("displayName") String displayName) {
    }
}
