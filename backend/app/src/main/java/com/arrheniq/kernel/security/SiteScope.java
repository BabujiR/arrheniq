package com.arrheniq.kernel.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Where a permission applies: every site of the tenant, or a fixed set of sites (URS-TEN-002/003). */
public record SiteScope(boolean allSites, Set<UUID> siteIds) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final SiteScope NONE = new SiteScope(false, Set.of());
    public static final SiteScope ALL = new SiteScope(true, Set.of());

    public SiteScope {
        siteIds = allSites ? Set.of() : Set.copyOf(siteIds);
    }

    public static SiteScope of(Set<UUID> sites) {
        return new SiteScope(false, sites);
    }

    public boolean includes(UUID siteId) {
        return allSites || (siteId != null && siteIds.contains(siteId));
    }

    public boolean isEmpty() {
        return !allSites && siteIds.isEmpty();
    }

    public SiteScope union(SiteScope other) {
        if (allSites || other.allSites) {
            return ALL;
        }
        Set<UUID> merged = new HashSet<>(siteIds);
        merged.addAll(other.siteIds);
        return new SiteScope(false, merged);
    }
}
