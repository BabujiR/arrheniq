package com.arrheniq.kernel.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 */
@ConfigurationProperties("arrheniq.security")
public record ArrheniqSecurityProperties(
        String registrationId,
        String baseDomain,
        String fixedTenantSlug,
        Duration absoluteSessionLifetime,
        Duration defaultIdleLock,
        Duration defaultLogoff,
        Duration reauthFlowTimeout,
        Duration reauthMaxAge,
        String postLoginPath) {

    public ArrheniqSecurityProperties {
        registrationId = blankTo(registrationId, "arrheniq");
        absoluteSessionLifetime = absoluteSessionLifetime == null ? Duration.ofHours(12) : absoluteSessionLifetime;
        defaultIdleLock = defaultIdleLock == null ? Duration.ofMinutes(30) : defaultIdleLock;
        defaultLogoff = defaultLogoff == null ? Duration.ofMinutes(60) : defaultLogoff;
        reauthFlowTimeout = reauthFlowTimeout == null ? Duration.ofMinutes(10) : reauthFlowTimeout;
        reauthMaxAge = reauthMaxAge == null ? Duration.ofMinutes(5) : reauthMaxAge;
        postLoginPath = SafeRedirects.orDefault(postLoginPath, "/");
        if (fixedTenantSlug != null && !fixedTenantSlug.isBlank() && !TenantHosts.isValidSlug(fixedTenantSlug)) {
            throw new IllegalArgumentException("arrheniq.security.fixed-tenant-slug is not a valid slug");
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
