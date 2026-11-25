package com.arrheniq.kernel.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission an application-service method needs (deny by default, URS-SEC-001,
 * URS-IAM-005). Example: {@code @RequirePermission("study.approve")}. The site of the target record
 * is checked separately with {@link Access#requireAt}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("@access.has('{value}')")
public @interface RequirePermission {

    /** Permission string, e.g. {@code result.approve.final}. */
    String value();
}
