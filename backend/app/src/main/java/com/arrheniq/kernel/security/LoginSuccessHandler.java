package com.arrheniq.kernel.security;

import com.arrheniq.kernel.audit.FieldChange;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * After a successful IdP callback:
 * <ul>
 *   <li><b>login</b>: start the session clock, apply the tenant's idle-lock/logoff times, record
 *       last login and audit LOGIN_SUCCESS</li>
 *   <li><b>re-authentication</b>: store single-use {@link ReauthEvidence}, unlock the session, audit
 *       REAUTH_SUCCESS; the original login time (absolute lifetime) is kept</li>
 * </ul>
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SecurityAuditor auditor;
    private final UserDirectory users;
    private final SessionSettings settings;
    private final ArrheniqSecurityProperties props;
    private final Clock clock;

    public LoginSuccessHandler(SecurityAuditor auditor, UserDirectory users, SessionSettings settings,
                               ArrheniqSecurityProperties props, Clock clock) {
        this.auditor = auditor;
        this.users = users;
        this.settings = settings;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof ArrheniqUser user)) {
            throw new IllegalStateException("ARQ-AUTH-003: unexpected principal type");
        }
        HttpSession session = request.getSession();
        Instant now = clock.instant();
        Instant authTime = user.getAuthenticatedAt() != null ? user.getAuthenticatedAt() : now;

        if (session.getAttribute(SessionKeys.PENDING_REAUTH) instanceof ReauthRequest pending) {
            session.removeAttribute(SessionKeys.PENDING_REAUTH);
            session.setAttribute(SessionKeys.REAUTH_EVIDENCE,
                    new ReauthEvidence(user.tenantId(), user.userId(), pending.purpose(), authTime, now,
                            pending.intent()));
            session.removeAttribute(SessionKeys.LOCKED);
            session.setAttribute(SessionKeys.LAST_ACTIVITY, now);
            auditor.record(user.tenantId(), SecurityAuditor.actorFor(user), "REAUTH_SUCCESS", user.userId(),
                    user.username(), reauthChanges(pending, authTime));
            response.sendRedirect(SafeRedirects.orDefault(pending.returnTo(), props.postLoginPath()));
            return;
        }

        SessionSettings.Times times = auditor.inTenantTransaction(user.tenantId(), "security.login", () -> {
            users.recordLogin(user.userId());
            auditor.recordInCurrentTransaction(SecurityAuditor.actorFor(user), "LOGIN_SUCCESS", user.userId(),
                    user.username(), List.of(new FieldChange("authTime", null, authTime),
                            new FieldChange("idpIssuer", null, String.valueOf(user.getIdToken().getIssuer()))));
            return settings.forCurrentTenant();
        });
        session.setAttribute(SessionKeys.LOGIN_AT, now);
        session.setAttribute(SessionKeys.LAST_ACTIVITY, now);
        session.setAttribute(SessionKeys.IDLE_LOCK, times.idleLock());
        session.setAttribute(SessionKeys.LOGIN_HINT, user.getPreferredUsername());
        session.removeAttribute(SessionKeys.LOCKED);
        session.removeAttribute(SessionKeys.REAUTH_EVIDENCE);
        session.setMaxInactiveInterval((int) times.logoff().toSeconds());
        String returnTo = session.getAttribute(SessionKeys.RETURN_TO) instanceof String r ? r : null;
        session.removeAttribute(SessionKeys.RETURN_TO);
        response.sendRedirect(SafeRedirects.orDefault(returnTo, props.postLoginPath()));
    }

    /** What the REAUTH_SUCCESS audit event records; for SIGN also the record the evidence is bound to. */
    private static List<FieldChange> reauthChanges(ReauthRequest pending, Instant authTime) {
        List<FieldChange> changes = new ArrayList<>();
        changes.add(new FieldChange("purpose", null, pending.purpose().name()));
        changes.add(new FieldChange("authTime", null, authTime));
        SigningIntent intent = pending.intent();
        if (intent != null) {
            changes.add(new FieldChange("signingIntent", null, intent.id()));
            changes.add(new FieldChange("intentRecord", null,
                    intent.recordType() + ":" + intent.recordId() + "@v" + intent.recordVersion()));
        }
        return changes;
    }
}
