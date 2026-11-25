package com.arrheniq.kernel.tenancy;

import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Holds the {@link TenantContext} for the current thread (virtual threads: one per request).
 * Always use {@link #runAs(TenantContext, Runnable)} or clear in a finally block, so a context can
 * never leak to another request.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() { }

    public static Optional<TenantContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current context, or an exception: GxP code paths must never run without a tenant. */
    public static TenantContext require() {
        TenantContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("ARQ-TENANT-001: no tenant context bound to this thread");
        }
        return ctx;
    }

    public static void runAs(TenantContext context, Runnable work) {
        callAs(context, () -> {
            work.run();
            return null;
        });
    }

    /** Runs {@code work} with {@code context} bound and returns its result; restores the previous context. */
    public static <T extends @Nullable Object> T callAs(TenantContext context, Supplier<T> work) {
        TenantContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
