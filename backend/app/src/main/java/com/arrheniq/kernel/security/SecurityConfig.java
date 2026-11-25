package com.arrheniq.kernel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deny-by-default security baseline (docs/architecture/security-architecture.md §4; URS-SEC-001).
 * <p>
 * Until slice S0 adds the OIDC/BFF login (ADR 0005, 0006), the only reachable endpoints are the
 * liveness/readiness probes. Every other request is denied, never silently permitted.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .anyRequest().denyAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
        return http.build();
    }
}
