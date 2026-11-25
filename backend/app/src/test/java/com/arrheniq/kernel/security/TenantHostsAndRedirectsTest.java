package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantHostsAndRedirectsTest {

    @Test
    @Requirement("URS-TEN-001")
    void tenantSlugComesFromExactlyOneSubdomainLabel() {
        assertEquals(Optional.of("acme"), TenantHosts.slugFromHost("acme.arrheniq.com", "arrheniq.com"));
        assertEquals(Optional.of("acme"), TenantHosts.slugFromHost("ACME.ArrheniQ.com:443", "arrheniq.com"));
        assertEquals(Optional.of("acme"), TenantHosts.slugFromHost("acme.arrheniq.com.", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("arrheniq.com", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("a.b.arrheniq.com", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("acme.arrheniq.com.evil.io", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("acmearrheniq.com", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("_x.arrheniq.com", "arrheniq.com"));
        assertEquals(Optional.empty(), TenantHosts.slugFromHost("[::1]:8080", "arrheniq.com"));
    }

    @Test
    void onlySameOriginRelativePathsAreAcceptedAsRedirectTargets() {
        assertTrue(SafeRedirects.isSafe("/studies/42?tab=pulls"));
        assertFalse(SafeRedirects.isSafe("https://evil.io/"));
        assertFalse(SafeRedirects.isSafe("//evil.io/x"));
        assertFalse(SafeRedirects.isSafe("/\\evil.io"));
        assertFalse(SafeRedirects.isSafe("/x\r\nSet-Cookie: a=b"));
        assertFalse(SafeRedirects.isSafe("javascript:alert(1)"));
        assertEquals("/", SafeRedirects.orDefault("https://evil.io", "/"));
    }
}
