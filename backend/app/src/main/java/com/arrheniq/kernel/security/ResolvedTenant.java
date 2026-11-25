package com.arrheniq.kernel.security;

import java.util.UUID;

/** A tenant as seen at the edge of a request: id, slug, display name and lifecycle status. */
public record ResolvedTenant(UUID id, String slug, String displayName, String status) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
