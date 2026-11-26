package com.arrheniq.kernel.security;

import com.arrheniq.kernel.tenancy.TenantContext;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads the current user's status and effective permissions on every request (one query), so a
 * deactivated user or a revoked role loses access immediately (URS-IAM-005, URS-IAM-007).
 * Assignments count only on the days they are valid.
 */
@Component
public class CurrentUserLoader {

    private static final String[] ACTIVE = {"ACTIVE"};
    private static final String[] ACTIVE_OR_INVITED = {"ACTIVE", "INVITED"};

    private final JdbcClient jdbc;
    private final TransactionTemplate readOnly;

    public CurrentUserLoader(JdbcClient jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.readOnly = new TransactionTemplate(txManager);
        this.readOnly.setReadOnly(true);
    }

    /** Empty if the user no longer exists, is not ACTIVE, or the tenant is not ACTIVE. */
    public Optional<CurrentUser> load(ArrheniqUser principal) {
        return load(principal.tenantId(), principal.tenantSlug(), principal.userId(), ACTIVE);
    }

    /**
     * As {@link #load(ArrheniqUser)}, for a user known by id, for {@link LocalUserContext} only: an INVITED user
     * (not signed in yet, so not linked to the IdP) also counts, so local demo data can be created before anyone
     * has signed in.
     */
    Optional<CurrentUser> loadForLocalDev(UUID tenantId, String tenantSlug, UUID userId) {
        return load(tenantId, tenantSlug, userId, ACTIVE_OR_INVITED);
    }

    private Optional<CurrentUser> load(UUID tenantId, String tenantSlug, UUID userId, String[] statuses) {
        List<Optional<CurrentUser>> result = new ArrayList<>(1);
        TenantContextHolder.runAs(TenantContext.system(tenantId),
                () -> readOnly.executeWithoutResult(_ -> result.add(query(tenantId, tenantSlug, userId, statuses))));
        return result.getFirst();
    }

    private Optional<CurrentUser> query(UUID tenantId, String tenantSlug, UUID userId, String[] statuses) {
        boolean activeTenant = jdbc.sql("SELECT status = 'ACTIVE' FROM platform.tenant WHERE id = ?")
                .param(tenantId).query(Boolean.class).optional().orElse(false);
        if (!activeTenant) {
            return Optional.empty();
        }
        Optional<String[]> user = jdbc.sql("""
                SELECT username, display_name FROM app.app_user
                 WHERE id = ? AND status = ANY (CAST(? AS text[])) AND deleted_at IS NULL
                """)
                .params(userId, statuses)
                .query((rs, _) -> new String[] {rs.getString(1), rs.getString(2)})
                .optional();
        if (user.isEmpty()) {
            return Optional.empty();
        }
        List<AccessGrants.Grant> grants = jdbc.sql("""
                SELECT rp.permission, usr.site_id
                  FROM app.user_site_role usr
                  JOIN app.role r            ON r.tenant_id = usr.tenant_id AND r.id = usr.role_id
                                            AND r.deleted_at IS NULL
                  JOIN app.role_permission rp ON rp.tenant_id = r.tenant_id AND rp.role_id = r.id
                                            AND rp.deleted_at IS NULL
                 WHERE usr.user_id = ? AND usr.deleted_at IS NULL
                   AND usr.valid_from <= current_date AND (usr.valid_to IS NULL OR usr.valid_to >= current_date)
                """)
                .param(userId)
                .query((rs, _) -> new AccessGrants.Grant(rs.getString(1), rs.getObject(2, UUID.class)))
                .list();
        return Optional.of(new CurrentUser(tenantId, tenantSlug, userId,
                user.get()[0], user.get()[1], AccessGrants.from(grants)));
    }
}
