package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * The signed-in user as stored in the session: the IdP identity plus the ArrheniQ tenant and user.
 * Permissions are deliberately <b>not</b> stored here; they are loaded per request
 * ({@link CurrentUserLoader}) so revoking a role or deactivating a user takes effect at once.
 */
public final class ArrheniqUser extends DefaultOidcUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID tenantId;
    private final String tenantSlug;
    private final UUID userId;
    private final String username;
    private final String displayName;

    public ArrheniqUser(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken,
                        @Nullable OidcUserInfo userInfo,
                        UUID tenantId, String tenantSlug, UUID userId, String username, String displayName) {
        super(authorities, idToken, userInfo, "sub");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.tenantSlug = Objects.requireNonNull(tenantSlug, "tenantSlug");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.username = Objects.requireNonNull(username, "username");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /** Principal name {@code <tenant>:<user>}; Spring Session indexes it, so all sessions of a user can be found. */
    @Override
    public String getName() {
        return tenantId + ":" + userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String tenantSlug() {
        return tenantSlug;
    }

    public UUID userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    /** Printed name used in audit trails and signature manifestations (Part 11 §11.50). */
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof ArrheniqUser other && super.equals(o)
                && tenantId.equals(other.tenantId) && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tenantId, userId);
    }

    /** Stored form in the session (see {@link ArrheniqUserSessionForm}, {@link SessionJson}). */
    ArrheniqUserSessionForm sessionForm() {
        return ArrheniqUserSessionForm.of(this);
    }

    static ArrheniqUser fromSessionForm(ArrheniqUserSessionForm form) {
        return form.toUser();
    }
}
