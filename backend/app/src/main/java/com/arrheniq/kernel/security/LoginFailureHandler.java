package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A failed login leaves no session behind. A failed <b>re-authentication</b> ends the existing session
 * as well: if someone else answered the credential prompt at an unattended workstation, the original
 * user's session must not survive (security-architecture.md T2). Reasons are in the audit trail, not
 * in the redirect.
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        HttpSession session = request.getSession(false);
        boolean wasReauth = session != null && session.getAttribute(SessionKeys.PENDING_REAUTH) != null;
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/auth/signed-out?error=" + (wasReauth ? "reauth" : "login"));
    }
}
