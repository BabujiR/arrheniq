package com.arrheniq.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that any signed-in user may call without a specific permission (e.g. "who am I").
 * Exists so the planned architecture rule can require every controller method to declare either
 * {@link RequirePermission} or this annotation (security-architecture.md §4).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuthenticatedOnly {
}
