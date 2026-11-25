package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Who am I" for the SPA: identity, tenant, effective permissions and the sites the user can see (drives
 * which UI controls are shown and the site pickers; the server still enforces every permission). Also issues
 * the CSRF cookie.
 */
@RestController
public class MeController {

    /** A site the user can see. */
    public record SiteRef(UUID id, String code, String name, String timeZone) { }

    /** Response body of {@code GET /api/v1/me}. */
    public record Me(UUID userId, String username, String displayName, String tenant, Set<String> permissions,
                     boolean allSites, List<UUID> siteIds, List<SiteRef> sites, long idleLockSeconds) { }

    private final ArrheniqSecurityProperties props;
    private final JdbcClient jdbc;
    private final TransactionTemplate readOnly;

    public MeController(ArrheniqSecurityProperties props, JdbcClient jdbc, PlatformTransactionManager txManager) {
        this.props = props;
        this.jdbc = jdbc;
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    @AuthenticatedOnly
    @GetMapping("/api/v1/me")
    public Me me(HttpSession session, CsrfToken csrf) {
        csrf.getToken();   // materialises the deferred token so the XSRF-TOKEN cookie is sent
        CurrentUser u = CurrentUserHolder.require();
        SiteScope scope = u.grants().visibleSites();
        Duration idle = session.getAttribute(SessionKeys.IDLE_LOCK) instanceof Duration d ? d : props.defaultIdleLock();
        return new Me(u.userId(), u.username(), u.displayName(), u.tenantSlug(), u.grants().permissions(),
                scope.allSites(), scope.siteIds().stream().sorted().toList(), sites(scope), idle.toSeconds());
    }

    /** Active sites inside the scope (the tenant context is bound by the session filter when the scope isn't empty). */
    private List<SiteRef> sites(SiteScope scope) {
        if (scope.isEmpty()) {
            return List.of();
        }
        return readOnly.execute(status -> {
            String sql = "SELECT id, code, name, time_zone FROM app.site WHERE active AND deleted_at IS NULL"
                    + (scope.allSites() ? "" : " AND id = ANY (CAST(? AS uuid[]))") + " ORDER BY code";
            JdbcClient.StatementSpec spec = jdbc.sql(sql);
            if (!scope.allSites()) {
                spec = spec.param(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
            }
            return spec.query((rs, n) -> new SiteRef(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getString("time_zone"))).list();
        });
    }
}
