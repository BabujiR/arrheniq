package com.arrheniq.kernel.security;

import java.util.UUID;

/** The authenticated user of the current request with permissions freshly loaded from the database. */
public record CurrentUser(UUID tenantId, String tenantSlug, UUID userId, String username, String displayName,
                          AccessGrants grants) {
}
