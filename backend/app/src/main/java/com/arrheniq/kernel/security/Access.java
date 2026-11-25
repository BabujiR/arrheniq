package com.arrheniq.kernel.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Permission checks for the current user (security-architecture.md §4). Used by
 * {@link RequirePermission} ({@code @access.has('…')}) at the service boundary, and directly for
 * the site check once the target record (and so its site) is known.
 */
@Component("access")
public class Access {

    /** True if the current user holds the permission at any site. */
    public boolean has(String permission) {
        return CurrentUserHolder.current().map(u -> u.grants().has(permission)).orElse(false);
    }

    /** True if the current user holds the permission at this site. */
    public boolean hasAt(String permission, UUID siteId) {
        return CurrentUserHolder.current().map(u -> u.grants().hasAt(permission, siteId)).orElse(false);
    }

    /** Throws {@link AccessDeniedException} (HTTP 403) unless the permission is held at this site. */
    public void requireAt(String permission, UUID siteId) {
        if (!hasAt(permission, siteId)) {
            throw new AccessDeniedException("ARQ-AUTHZ-001: missing permission " + permission + " for site");
        }
    }
}
