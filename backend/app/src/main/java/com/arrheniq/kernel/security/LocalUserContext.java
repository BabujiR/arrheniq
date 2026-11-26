package com.arrheniq.kernel.security;

import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Local development only ({@code local} profile; the bean does not exist anywhere else): runs work as an existing
 * user of a tenant, with that user's real permissions and site scope, exactly as {@link SessionContextFilter} binds
 * them for a request. It lets the demo data go through the real, audited application services as the demo users
 * instead of writing rows directly.
 *
 * <p>It does not make e-signatures possible: signing needs an interactive session with an ID token and fresh
 * re-authentication ({@code SignatureService}), which this context deliberately does not have.
 */
@Component
@Profile("local")
public class LocalUserContext {

    private final JdbcClient jdbc;
    private final CurrentUserLoader loader;
    private final TransactionTemplate readOnly;

    LocalUserContext(JdbcClient jdbc, CurrentUserLoader loader, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.loader = loader;
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    /** Runs {@code work} as the ACTIVE user {@code username} of the tenant. */
    public void runAs(UUID tenantId, String username, Runnable work) {
        List<Optional<String[]>> found = new ArrayList<>(1);
        TenantContextHolder.runAs(TenantContext.system(tenantId), () -> readOnly.executeWithoutResult(_ -> found.add(
                jdbc.sql("""
                        SELECT t.slug, u.id::text FROM platform.tenant t, app.app_user u
                         WHERE t.id = ? AND u.username = ? AND u.deleted_at IS NULL
                        """)
                    .params(tenantId, username)
                    .query((rs, _) -> new String[] {rs.getString(1), rs.getString(2)})
                    .optional())));
        String[] row = found.getFirst().orElseThrow(() -> new IllegalStateException("no user " + username));
        CurrentUser user = loader.load(tenantId, row[0], UUID.fromString(row[1]))
                .orElseThrow(() -> new IllegalStateException("user " + username + " is not active"));
        SiteScope visible = user.grants().visibleSites();
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
        try {
            SecurityContextHolder.setContext(context);
            CurrentUserHolder.set(user);
            TenantContextHolder.runAs(new TenantContext(tenantId, Optional.of(user.userId()), visible.allSites(),
                    visible.siteIds()), work);
        } finally {
            CurrentUserHolder.clear();
            SecurityContextHolder.setContext(previous);
        }
    }
}
