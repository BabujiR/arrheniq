package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds the tenant a browser request belongs to (multi-tenancy.md §2): the configured fixed tenant
 * (Private/Dedicated, local), otherwise the host's subdomain. {@code platform.tenant} is readable by
 * the app role and has no RLS, so no tenant context is needed here.
 */
@Component
public class TenantResolver {

    private final JdbcClient jdbc;
    private final ArrheniqSecurityProperties props;

    public TenantResolver(JdbcClient jdbc, ArrheniqSecurityProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public Optional<ResolvedTenant> resolve(HttpServletRequest request) {
        Optional<String> slug = props.fixedTenantSlug() != null && !props.fixedTenantSlug().isBlank()
                ? Optional.of(props.fixedTenantSlug())
                : TenantHosts.slugFromHost(request.getServerName(), props.baseDomain());
        return slug.flatMap(this::bySlug);
    }

    public Optional<ResolvedTenant> bySlug(String slug) {
        return jdbc.sql("SELECT id, slug, display_name, status FROM platform.tenant WHERE slug = ?")
                .param(slug)
                .query((rs, _) -> new ResolvedTenant(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("display_name"), rs.getString("status")))
                .optional();
    }

    public Optional<ResolvedTenant> byId(UUID id) {
        return jdbc.sql("SELECT id, slug, display_name, status FROM platform.tenant WHERE id = ?")
                .param(id)
                .query((rs, _) -> new ResolvedTenant(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("display_name"), rs.getString("status")))
                .optional();
    }
}
