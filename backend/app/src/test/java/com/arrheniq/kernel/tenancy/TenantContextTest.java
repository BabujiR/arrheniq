package com.arrheniq.kernel.tenancy;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** URS-TEN-001/002: tenant context must be explicit, scoped and never leak. */
class TenantContextTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SITE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SITE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void siteScopeIsEnforced() {
        TenantContext ctx = new TenantContext(TENANT, Optional.empty(), false, Set.of(SITE_A));
        assertTrue(ctx.canAccessSite(SITE_A));
        assertFalse(ctx.canAccessSite(SITE_B));
        assertTrue(new TenantContext(TENANT, Optional.empty(), true, Set.of()).canAccessSite(SITE_B));
    }

    @Test
    void actorWithoutAnySiteScopeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TenantContext(TENANT, Optional.empty(), false, Set.of()));
    }

    @Test
    void requireFailsWhenNoContextIsBound() {
        assertThrows(IllegalStateException.class, TenantContextHolder::require);
    }

    @Test
    void contextIsRestoredAfterRunAs() {
        TenantContext ctx = new TenantContext(TENANT, Optional.empty(), true, Set.of());
        AtomicReference<UUID> seen = new AtomicReference<>();
        TenantContextHolder.runAs(ctx, () -> seen.set(TenantContextHolder.require().tenantId()));
        assertEquals(TENANT, seen.get());
        assertTrue(TenantContextHolder.current().isEmpty(), "context leaked after runAs");
    }
}
