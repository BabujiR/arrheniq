package com.arrheniq.kernel.security;

import com.arrheniq.testsupport.PostgresIntegrationTest;
import com.arrheniq.testsupport.Requirement;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * URS-SEC-001 acceptance test: calls <em>every</em> ArrheniQ endpoint without a session and expects it to be
 * refused before any application code runs: {@code /api/**} answers 401 problem JSON; other paths answer 401,
 * 403 or a redirect to the login. Endpoints marked {@link PublicEndpoint} are the only exception and must be
 * reachable anonymously. The endpoint list is read from Spring MVC, so new endpoints are covered automatically.
 * A valid CSRF cookie/header pair is sent (as the SPA does) so a refusal cannot come from the CSRF filter instead of
 * authentication. Deliberately not the {@code csrf()} test helper: it swaps the CSRF repository of the shared,
 * cached application context and breaks later tests.
 */
@Requirement("URS-SEC-001")
class EndpointSweepIT extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void everyEndpointRefusesAnonymousCallersExceptPublicOnes() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        List<String> problems = new ArrayList<>();
        int swept = 0;
        int publicOnes = 0;
        String token = UUID.randomUUID().toString();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMethods().entrySet()) {
            HandlerMethod handler = e.getValue();
            boolean isPublic = handler.hasMethodAnnotation(PublicEndpoint.class);
            for (String path : paths(e.getKey())) {
                for (HttpMethod method : methods(e.getKey())) {
                    MvcResult r = mvc.perform(request(method, path)
                            .cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
                    int status = r.getResponse().getStatus();
                    String where = method + " " + path + " (" + handler.getShortLogMessage() + ")";
                    swept++;
                    if (isPublic) {
                        publicOnes++;
                        if (status == 401 || status == 403) {
                            problems.add(where + ": marked @PublicEndpoint but refused anonymously (" + status
                                    + "); add it to permitAll() in SecurityConfig or remove the annotation");
                        }
                    } else if (path.startsWith("/api/")) {
                        if (status != 401 || !r.getResponse().getContentAsString().contains("ARQ-AUTH-401")) {
                            problems.add(where + ": expected 401 ARQ-AUTH-401, got " + status);
                        }
                    } else if (!(status == 401 || status == 403 || isLoginRedirect(r))) {
                        problems.add(where + ": expected 401/403 or a redirect to login, got " + status + " "
                                + r.getResponse().getRedirectedUrl());
                    }
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("Endpoints reachable without a session:\n  " + String.join("\n  ", problems));
        }
        assertTrue(swept >= 12, "sweep found too few endpoints (" + swept + "); is the handler mapping read correctly?");
        assertEquals(2, publicOnes, "expected exactly the two public endpoints: /auth/login and /auth/signed-out");
    }

    // ------------------------------------------------------------------ helpers

    /** ArrheniQ's own endpoints (not actuator, not Spring's /error). */
    private Map<RequestMappingInfo, HandlerMethod> handlerMethods() {
        Map<RequestMappingInfo, HandlerMethod> all = new java.util.LinkedHashMap<>();
        context.getBeansOfType(RequestMappingHandlerMapping.class).values()
                .forEach(m -> m.getHandlerMethods().forEach((info, h) -> {
                    if (h.getBeanType().getPackageName().startsWith("com.arrheniq")) {
                        all.put(info, h);
                    }
                }));
        return all;
    }

    /** Concrete paths for the mapping, with every {variable} filled by a random UUID. */
    private static Set<String> paths(RequestMappingInfo info) {
        Set<String> out = new java.util.TreeSet<>();
        if (info.getPathPatternsCondition() != null) {
            for (PathPattern p : info.getPathPatternsCondition().getPatterns()) {
                out.add(p.getPatternString().replaceAll("\\{[^}]+}", UUID.randomUUID().toString()));
            }
        }
        return out;
    }

    private static List<HttpMethod> methods(RequestMappingInfo info) {
        var declared = info.getMethodsCondition().getMethods();
        return declared.isEmpty()
                ? List.of(HttpMethod.GET)
                : declared.stream().map(m -> HttpMethod.valueOf(m.name())).toList();
    }

    private static boolean isLoginRedirect(MvcResult r) {
        String location = r.getResponse().getRedirectedUrl();
        return r.getResponse().getStatus() / 100 == 3 && location != null
                && (location.contains("/auth/login") || location.contains("/oauth2/authorization/")
                || location.endsWith("/login"));
    }
}
