package com.arrheniq.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is reachable without signing in (start of login, the signed-out landing page).
 * Must match the {@code permitAll()} paths in {@link SecurityConfig}; {@code EndpointSweepIT} checks that
 * every other endpoint answers 401 to an anonymous caller (URS-SEC-001).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PublicEndpoint {
}
