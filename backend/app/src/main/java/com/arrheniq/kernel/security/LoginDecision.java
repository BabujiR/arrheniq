package com.arrheniq.kernel.security;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides whether an identity confirmed by the IdP may enter a tenant (URS-IAM-001/002).
 * The IdP proves who the person is; the tenant's user directory decides whether they are allowed in.
 * <ul>
 *   <li>known subject + ACTIVE → allow</li>
 *   <li>unknown subject, an INVITED user with the same <b>verified</b> e-mail → link the invitation, then allow</li>
 *   <li>everything else → deny with a reason that is audited (never shown in detail to the user)</li>
 * </ul>
 */
public record LoginDecision(Outcome outcome, UUID userId, DenyReason denyReason) {

    public enum Outcome { ALLOW, LINK_INVITATION, DENY }

    public enum DenyReason { TENANT_NOT_ACTIVE, UNKNOWN_USER, USER_DEACTIVATED, USER_NOT_ACTIVE, EMAIL_NOT_VERIFIED }

    /** The fields of {@code app.app_user} the decision needs. */
    public record UserRecord(UUID id, String username, String email, String displayName, String status) {
        public UserRecord {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
        }
    }

    public static LoginDecision decide(String tenantStatus, Optional<UserRecord> bySubject,
                                       Optional<UserRecord> invitationByEmail, boolean emailVerified) {
        if (!"ACTIVE".equals(tenantStatus)) {
            return deny(null, DenyReason.TENANT_NOT_ACTIVE);
        }
        if (bySubject.isPresent()) {
            UserRecord u = bySubject.get();
            return switch (u.status()) {
                case "ACTIVE" -> new LoginDecision(Outcome.ALLOW, u.id(), null);
                case "DEACTIVATED" -> deny(u.id(), DenyReason.USER_DEACTIVATED);
                default -> deny(u.id(), DenyReason.USER_NOT_ACTIVE);
            };
        }
        if (invitationByEmail.isPresent() && "INVITED".equals(invitationByEmail.get().status())) {
            UserRecord u = invitationByEmail.get();
            return emailVerified
                    ? new LoginDecision(Outcome.LINK_INVITATION, u.id(), null)
                    : deny(u.id(), DenyReason.EMAIL_NOT_VERIFIED);
        }
        return deny(null, DenyReason.UNKNOWN_USER);
    }

    private static LoginDecision deny(UUID userId, DenyReason reason) {
        return new LoginDecision(Outcome.DENY, userId, reason);
    }

    public boolean allowed() {
        return outcome != Outcome.DENY;
    }
}
