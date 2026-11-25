package com.arrheniq.kernel.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Clock;

/**
 * Browser security (security-architecture.md §3–§4; ADR 0005, 0006; URS-SEC-001):
 * <ul>
 *   <li>OIDC authorization-code login (PKCE) through the backend; tokens stay server-side, the browser
 *       gets only the {@code ARQSESSION} cookie (Spring Session JDBC)</li>
 *   <li>deny by default: health probes and the login entry points are public, {@code /api/**} needs a
 *       session, everything else is denied</li>
 *   <li>API calls without a session get 401 problem JSON, not a redirect</li>
 *   <li>CSRF double-submit cookie for the SPA; strict CSP and the default security headers</li>
 *   <li>{@link SessionContextFilter} applies session rules and binds user, permissions and tenant</li>
 *   <li>method security: {@link RequirePermission} on application services</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(ArrheniqSecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
                                            ArrheniqOidcUserService oidcUserService, LoginSuccessHandler onSuccess,
                                            LoginFailureHandler onFailure, AuditLogoutHandler auditLogout,
                                            CurrentUserLoader loader, TenantResolver tenants, SecurityAuditor auditor,
                                            ArrheniqSecurityProperties props, Clock clock) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(new ReauthAuthorizationRequestCustomizer());

        OidcClientInitiatedLogoutSuccessHandler logoutSuccess = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutSuccess.setPostLogoutRedirectUri("{baseUrl}/auth/signed-out");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers("/auth/login", "/auth/signed-out", "/error").permitAll()
                .requestMatchers("/api/**", "/auth/reauth").authenticated()
                .anyRequest().denyAll())
            .oauth2Login(login -> login
                .loginPage("/auth/login")
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                .successHandler(onSuccess)
                .failureHandler(onFailure))
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .addLogoutHandler(auditLogout)
                .logoutSuccessHandler(logoutSuccess))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())   // SPA must read XSRF-TOKEN
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(new ProblemAuthenticationEntryPoint(),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'")))
            .addFilterAfter(new SessionContextFilter(loader, tenants, auditor, props, clock), AuthorizationFilter.class)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Lets {@link RequirePermission} use the {@code '{value}'} placeholder in its {@code @PreAuthorize}. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
