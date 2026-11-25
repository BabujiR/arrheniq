package com.arrheniq.identity;

import org.jspecify.annotations.Nullable;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.security.UserDirectory;
import com.arrheniq.kernel.tenancy.TenantContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Invites a user into the current tenant: an INVITED account with role assignments. The person's
 * IdP identity is linked at their first login with the same verified e-mail (kernel.security).
 * Every write is audited (URS-IAM-007). The admin API (S1) will put {@code @RequirePermission("user.manage")}
 * on its entry point; this service is also used by provisioning and local dev data.
 */
@Service
public class UserInvitationService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,63}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** A role to assign; {@code siteCode == null} means all sites. */
    public record RoleAssignment(String roleCode, @Nullable String siteCode) { }

    public record Invitation(String username, String email, String displayName, List<RoleAssignment> roles) {
        public Invitation {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(displayName, "displayName");
            roles = List.copyOf(roles);
        }
    }

    private final JdbcClient jdbc;
    private final AuditService audit;

    public UserInvitationService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Creates the invited user; returns its id. Requires a tenant transaction with an open audit context. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID invite(AuditActor actor, Invitation inv) {
        TenantContextHolder.require();
        String username = inv.username().trim().toLowerCase(Locale.ROOT);
        String email = inv.email().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("ARQ-IAM-001: invalid username");
        }
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("ARQ-IAM-002: invalid e-mail");
        }
        if (inv.displayName().isBlank()) {
            throw new IllegalArgumentException("ARQ-IAM-003: display name required");
        }
        Boolean taken = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.app_user WHERE username = ?)")
                .param(username).query(Boolean.class).single();
        if (taken) {   // usernames are never reused, not even after deactivation (URS-IAM-001)
            throw new IllegalArgumentException("ARQ-IAM-004: username already used");
        }

        UUID userId = UUID.randomUUID();
        UUID actorId = actor.userId();
        jdbc.sql("""
                INSERT INTO app.app_user (tenant_id, id, created_by, idp_issuer, idp_subject, username, email,
                                          display_name, status)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, 'INVITED')
                """)
            .params(userId, actorId, UserDirectory.INVITATION_ISSUER, userId.toString(), username, email,
                    inv.displayName().trim())
            .update();
        audit.record(actor, new AuditEventDraft("CREATE", "USER", userId, 0L, username, null,
                List.of(new FieldChange("username", null, username), new FieldChange("email", null, email),
                        new FieldChange("displayName", null, inv.displayName().trim()),
                        new FieldChange("status", null, "INVITED")),
                null, null, null));

        for (RoleAssignment ra : inv.roles()) {
            assign(actor, userId, username, ra);
        }
        return userId;
    }

    private void assign(AuditActor actor, UUID userId, String username, RoleAssignment ra) {
        UUID roleId = jdbc.sql("SELECT id FROM app.role WHERE code = ? AND deleted_at IS NULL")
                .param(ra.roleCode()).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("ARQ-IAM-005: unknown role " + ra.roleCode()));
        UUID siteId = null;
        if (ra.siteCode() != null) {
            siteId = jdbc.sql("SELECT id FROM app.site WHERE code = ? AND deleted_at IS NULL")
                    .param(ra.siteCode()).query(UUID.class).optional()
                    .orElseThrow(() -> new IllegalArgumentException("ARQ-IAM-006: unknown site " + ra.siteCode()));
        }
        UUID assignmentId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO app.user_site_role (tenant_id, id, created_by, user_id, role_id, site_id)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?)
                """)
            .params(assignmentId, actor.userId(), userId, roleId, siteId)
            .update();
        List<FieldChange> changes = new ArrayList<>();
        changes.add(new FieldChange("user", null, username));
        changes.add(new FieldChange("role", null, ra.roleCode()));
        changes.add(new FieldChange("site", null, ra.siteCode() == null ? "ALL_SITES" : ra.siteCode()));
        audit.record(actor, new AuditEventDraft("CREATE", "USER_SITE_ROLE", assignmentId, 0L, username, siteId,
                changes, null, null, null));
    }
}
