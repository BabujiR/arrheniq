package com.arrheniq.kernel.tenancy;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant and site scope of the current request or job (docs/architecture/multi-tenancy.md §2).
 * <p>
 * Bound by the web layer (from the BFF session or API key) or by the job runner. The persistence
 * layer copies {@link #tenantId()} into the PostgreSQL transaction setting {@code app.tenant_id},
 * where row-level security enforces isolation (fails closed if not set).
 *
 * @param tenantId     isolation boundary
 * @param userId       acting user; empty for system jobs
 * @param allSites     true if the actor may access every site of the tenant
 * @param siteIds      site scope when {@code allSites} is false
 */
public record TenantContext(UUID tenantId, Optional<UUID> userId, boolean allSites, Set<UUID> siteIds) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        siteIds = Set.copyOf(siteIds);
        if (!allSites && siteIds.isEmpty()) {
            throw new IllegalArgumentException("actor without site scope");
        }
    }

    /**
     * Context for platform-initiated work inside a tenant (login, session checks, jobs): tenant
     * isolation applies, no user, all sites. Never use it for work a user asked for.
     */
    public static TenantContext system(UUID tenantId) {
        return new TenantContext(tenantId, Optional.empty(), true, Set.of());
    }

    public boolean canAccessSite(UUID siteId) {
        return allSites || siteIds.contains(siteId);
    }
}
