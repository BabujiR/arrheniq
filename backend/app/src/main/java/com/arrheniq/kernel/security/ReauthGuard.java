package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Used by the e-signature service: a signature may be executed only with fresh, single-use
 * re-authentication evidence for the current user (URS-ESG-002; audit-esignature.md).
 */
@Component
public class ReauthGuard {

    private final ArrheniqSecurityProperties props;
    private final Clock clock;

    public ReauthGuard(ArrheniqSecurityProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** Consumes the evidence (single use) and returns it; throws if missing, stale or for another user. */
    public ReauthEvidence consumeForSignature(HttpSession session) {
        CurrentUser user = CurrentUserHolder.require();
        Object value = session.getAttribute(SessionKeys.REAUTH_EVIDENCE);
        session.removeAttribute(SessionKeys.REAUTH_EVIDENCE);
        ReauthEvidence evidence = value instanceof ReauthEvidence e ? e : null;
        if (!ReauthPolicy.isUsable(evidence, user.tenantId(), user.userId(), ReauthRequest.Purpose.SIGN,
                clock.instant(), props.reauthMaxAge())) {
            throw new ReauthRequiredException();
        }
        return evidence;
    }
}
