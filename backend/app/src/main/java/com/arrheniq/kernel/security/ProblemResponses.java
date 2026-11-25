package com.arrheniq.kernel.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Minimal RFC 9457 problem responses for the security filters (no stack traces, stable codes). */
final class ProblemResponses {

    private ProblemResponses() { }

    static void write(HttpServletResponse response, int status, String title, String code) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        // code and title are internal constants, never user input
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"code\":\"" + code + "\"}");
    }
}
