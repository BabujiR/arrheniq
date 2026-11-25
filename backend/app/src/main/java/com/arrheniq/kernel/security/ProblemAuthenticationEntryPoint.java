package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** API requests without a session get 401 problem JSON, never a redirect to the IdP. */
final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemResponses.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "ARQ-AUTH-401");
    }
}
