/**
 * Identity administration: users, invitations, role assignments (URS-IAM-001, -005, -006, -007).
 * Login and per-request authorization live in the kernel ({@code kernel.security}).
 */
@org.jspecify.annotations.NullMarked
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.arrheniq.identity;
