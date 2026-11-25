package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/** Audits an explicit logout before the session is invalidated. */
@Component
public class AuditLogoutHandler implements LogoutHandler {

    private final SecurityAuditor auditor;

    public AuditLogoutHandler(SecurityAuditor auditor) {
        this.auditor = auditor;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ArrheniqUser user) {
            auditor.record(user.tenantId(), SecurityAuditor.actorFor(user), "LOGOUT", user.userId(),
                    user.username(), List.of());
        }
    }
}
