package com.arrheniq.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method that runs as the system (tenant provisioning, local seed data, jobs), not on
 * behalf of a signed-in user, so it has no {@link RequirePermission}. The architecture rules
 * ({@code EndpointAuthorizationRulesTest}) forbid calling such a method from a controller.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SystemOperation {
}
