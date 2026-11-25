package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Consumer;

/**
 * Adds {@code prompt=login&max_age=0&login_hint=<user>} to the OIDC authorization request when a
 * re-authentication is pending, so the IdP must ask for credentials again (ADR 0005). The result is
 * still verified on the way back ({@link ReauthPolicy}); the IdP is not trusted to comply.
 */
final class ReauthAuthorizationRequestCustomizer implements Consumer<OAuth2AuthorizationRequest.Builder> {

    static final String REAUTH_PARAM = "reauth";

    @Override
    public void accept(OAuth2AuthorizationRequest.Builder builder) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        if (!"1".equals(request.getParameter(REAUTH_PARAM))) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionKeys.PENDING_REAUTH) instanceof ReauthRequest)) {
            return;
        }
        String hint = session.getAttribute(SessionKeys.LOGIN_HINT) instanceof String h ? h : null;
        builder.additionalParameters(params -> {
            params.put("prompt", "login");
            params.put("max_age", "0");
            if (hint != null) {
                params.put("login_hint", hint);
            }
        });
    }
}
