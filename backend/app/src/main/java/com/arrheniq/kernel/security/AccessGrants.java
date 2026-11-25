package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The effective permissions of a user: permission → site scope (security-architecture.md §4).
 * Built from active (user, role, site) assignments; a {@code null} site means all sites.
 */
public final class AccessGrants {

    /** One row of the assignment query: a permission granted at a site (null = all sites). */
    public record Grant(@Nullable String permission, @Nullable UUID siteId) { }

    private final Map<String, SiteScope> byPermission;

    private AccessGrants(Map<String, SiteScope> byPermission) {
        this.byPermission = Map.copyOf(byPermission);
    }

    public static AccessGrants none() {
        return new AccessGrants(Map.of());
    }

    public static AccessGrants from(Collection<Grant> grants) {
        Map<String, SiteScope> map = new HashMap<>();
        for (Grant g : grants) {
            if (g.permission() == null || g.permission().isBlank()) {
                continue;
            }
            SiteScope scope = g.siteId() == null ? SiteScope.ALL : SiteScope.of(Set.of(g.siteId()));
            map.merge(g.permission(), scope, SiteScope::union);
        }
        return new AccessGrants(map);
    }

    /** True if the permission is granted at any site. */
    public boolean has(String permission) {
        SiteScope s = byPermission.get(permission);
        return s != null && !s.isEmpty();
    }

    /** True if the permission is granted at this site. */
    public boolean hasAt(String permission, UUID siteId) {
        SiteScope s = byPermission.get(permission);
        return s != null && s.includes(siteId);
    }

    public SiteScope scopeOf(String permission) {
        return byPermission.getOrDefault(permission, SiteScope.NONE);
    }

    /** Sites the user can see at all (union over every permission); drives the tenant context. */
    public SiteScope visibleSites() {
        SiteScope result = SiteScope.NONE;
        for (SiteScope s : byPermission.values()) {
            result = result.union(s);
        }
        return result;
    }

    public Set<String> permissions() {
        return new TreeSet<>(byPermission.keySet());
    }
}
