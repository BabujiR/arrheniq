package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.FieldChange;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login-time access to {@code app.app_user}. All methods run inside a tenant transaction (RLS).
 * Invited users carry a placeholder IdP identity until their first login links the real one.
 */
@Component
public class UserDirectory {

    /** Issuer placeholder for users who were invited but never logged in. */
    public static final String INVITATION_ISSUER = "urn:arrheniq:invitation";

    private static final RowMapper<LoginDecision.UserRecord> USER = (rs, _) -> new LoginDecision.UserRecord(
            rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"),
            rs.getString("display_name"), rs.getString("status"));

    private static final String COLUMNS = "id, username, email, display_name, status";

    private final JdbcClient jdbc;
    private final AuditService audit;

    public UserDirectory(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoginDecision.UserRecord> findBySubject(String issuer, String subject) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app.app_user"
                        + " WHERE idp_issuer = ? AND idp_subject = ? AND deleted_at IS NULL")
                .params(issuer, subject)
                .query(USER)
                .optional();
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoginDecision.UserRecord> findById(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app.app_user WHERE id = ? AND deleted_at IS NULL")
                .param(userId)
                .query(USER)
                .optional();
    }

    /** The open invitation for this e-mail (case-insensitive), if exactly one exists. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<LoginDecision.UserRecord> findInvitationByEmail(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<LoginDecision.UserRecord> found = jdbc.sql("SELECT " + COLUMNS + " FROM app.app_user"
                        + " WHERE lower(email) = lower(?) AND status = 'INVITED' AND idp_issuer = ?"
                        + " AND deleted_at IS NULL")
                .params(email.trim(), INVITATION_ISSUER)
                .query(USER)
                .list();
        return found.size() == 1 ? Optional.of(found.getFirst()) : Optional.empty();
    }

    /** First login of an invited user: bind the IdP identity and activate (audited, URS-IAM-007). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkInvitation(LoginDecision.UserRecord user, String issuer, String subject) {
        int updated = jdbc.sql("""
                UPDATE app.app_user
                   SET idp_issuer = ?, idp_subject = ?, status = 'ACTIVE',
                       updated_at = clock_timestamp(), updated_by = id, version = version + 1
                 WHERE id = ? AND status = 'INVITED' AND idp_issuer = ?
                """)
                .params(issuer, subject, user.id(), INVITATION_ISSUER)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("ARQ-AUTH-010: invitation for user " + user.id() + " is no longer open");
        }
        audit.record(new AuditActor(user.id(), user.displayName(), AuditActor.Type.USER),
                new AuditEventDraft("UPDATE", "USER", user.id(), null, user.username(), null,
                        List.of(new FieldChange("idpIssuer", INVITATION_ISSUER, issuer),
                                new FieldChange("idpSubject", "(invitation)", subject),
                                new FieldChange("status", "INVITED", "ACTIVE")),
                        "INVITED", null, "FIRST_LOGIN"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLogin(UUID userId) {
        jdbc.sql("UPDATE app.app_user SET last_login_at = clock_timestamp() WHERE id = ?").param(userId).update();
    }
}
