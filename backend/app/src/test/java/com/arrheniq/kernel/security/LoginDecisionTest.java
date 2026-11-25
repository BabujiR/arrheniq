package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.Requirement;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Requirement({"URS-IAM-001", "URS-IAM-002"})
class LoginDecisionTest {

    private static final UUID ID = UUID.randomUUID();

    private static Optional<LoginDecision.UserRecord> user(String status) {
        return Optional.of(new LoginDecision.UserRecord(ID, "sam", "sam@x.test", "Sam", status));
    }

    @Test
    void activeKnownUserIsAllowed() {
        LoginDecision d = LoginDecision.decide("ACTIVE", user("ACTIVE"), Optional.empty(), true);
        assertEquals(LoginDecision.Outcome.ALLOW, d.outcome());
        assertEquals(ID, d.userId());
    }

    @Test
    void deactivatedUserIsDenied() {
        assertEquals(LoginDecision.DenyReason.USER_DEACTIVATED,
                LoginDecision.decide("ACTIVE", user("DEACTIVATED"), Optional.empty(), true).denyReason());
    }

    @Test
    void suspendedTenantDeniesEveryone() {
        assertEquals(LoginDecision.DenyReason.TENANT_NOT_ACTIVE,
                LoginDecision.decide("SUSPENDED", user("ACTIVE"), Optional.empty(), true).denyReason());
    }

    @Test
    void invitationIsLinkedOnlyWithVerifiedEmail() {
        assertEquals(LoginDecision.Outcome.LINK_INVITATION,
                LoginDecision.decide("ACTIVE", Optional.empty(), user("INVITED"), true).outcome());
        assertEquals(LoginDecision.DenyReason.EMAIL_NOT_VERIFIED,
                LoginDecision.decide("ACTIVE", Optional.empty(), user("INVITED"), false).denyReason());
    }

    @Test
    void unknownIdentityIsDenied() {
        assertEquals(LoginDecision.DenyReason.UNKNOWN_USER,
                LoginDecision.decide("ACTIVE", Optional.empty(), Optional.empty(), true).denyReason());
        // a deactivated account with the same e-mail is not an invitation (usernames are never reused)
        assertEquals(LoginDecision.DenyReason.UNKNOWN_USER,
                LoginDecision.decide("ACTIVE", Optional.empty(), user("DEACTIVATED"), true).denyReason());
    }
}
