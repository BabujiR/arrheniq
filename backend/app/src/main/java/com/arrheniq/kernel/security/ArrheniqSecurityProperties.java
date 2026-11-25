package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Security settings ({@code arrheniq.security.*}). Per-tenant session times come from tenant settings;
 * these are platform limits and fallbacks.
 *
 * @param registrationId          OIDC client registration used for browser login
 * @param baseDomain              tenant hosts are {@code <slug>.<baseDomain>} (cloud); blank = not used
 * @param fixedTenantSlug         single-tenant installs (Private/Dedicated) and local development
 * @param absoluteSessionLifetime hard limit for one login, whatever the activity
 * @param defaultIdleLock         used if the tenant setting {@code session.idle_lock_minutes} is missing
 * @param defaultLogoff           used if the tenant setting {@code session.logoff_minutes} is missing
 * @param reauthFlowTimeout       a started re-authentication must finish within this time
 * @param reauthMaxAge            re-authentication evidence is usable for this long (signatures)
 * @param postLoginPath           where to go after login when no safe return path was given
 * @param signedOutPath           SPA page shown after logout or a failed login ({@code ?error=login|reauth})
 */
@ConfigurationProperties("arrheniq.security")
public record ArrheniqSecurityProperties(
        @DefaultValue("arrheniq") String registrationId,
        @Nullable String baseDomain,
        @Nullable String fixedTenantSlug,
        @DefaultValue("12h") Duration absoluteSessionLifetime,
        @DefaultValue("30m") Duration defaultIdleLock,
        @DefaultValue("60m") Duration defaultLogoff,
        @DefaultValue("10m") Duration reauthFlowTimeout,
        @DefaultValue("5m") Duration reauthMaxAge,
        @DefaultValue("/") String postLoginPath,
        @DefaultValue("/signed-out") String signedOutPath) {

    /** Spring supplies the defaults above for missing properties; wrong values stop the application at startup. */
    public ArrheniqSecurityProperties {
        require(!registrationId.isBlank(), "registration-id must not be blank");
        requirePositive(absoluteSessionLifetime, "absolute-session-lifetime");
        requirePositive(defaultIdleLock, "default-idle-lock");
        requirePositive(defaultLogoff, "default-logoff");
        requirePositive(reauthFlowTimeout, "reauth-flow-timeout");
        requirePositive(reauthMaxAge, "reauth-max-age");
        require(SafeRedirects.isSafe(postLoginPath), "post-login-path must be a same-origin path such as /");
        require(SafeRedirects.isSafe(signedOutPath), "signed-out-path must be a same-origin path such as /signed-out");
        if (fixedTenantSlug != null && !fixedTenantSlug.isBlank()) {
            require(TenantHosts.isValidSlug(fixedTenantSlug), "fixed-tenant-slug is not a valid tenant slug");
        }
    }

    private static void requirePositive(Duration d, String name) {
        require(!d.isNegative() && !d.isZero(), name + " must be greater than zero");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("arrheniq.security." + message);
        }
    }
}
