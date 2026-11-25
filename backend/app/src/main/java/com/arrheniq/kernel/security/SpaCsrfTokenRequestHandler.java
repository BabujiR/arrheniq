package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

/**
 * CSRF handling for the React SPA (ADR 0006):
 * <ul>
 *   <li>the token is loaded on every request, so the {@code XSRF-TOKEN} cookie is always present for the SPA</li>
 *   <li>the SPA echoes the raw cookie value, in the {@code X-XSRF-TOKEN} header for API calls and in the
 *       {@code _csrf} field of the logout form (a form POST, so the browser can follow the redirect to the IdP's
 *       logout page)</li>
 *   <li>XOR masking (BREACH protection) is not needed: the server never renders the token into an HTML page</li>
 * </ul>
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
        csrfToken.get();   // materialise the deferred token so the cookie is written on this response
    }

    @Override
    public @Nullable String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        return this.plain.resolveCsrfTokenValue(request, csrfToken);
    }
}
