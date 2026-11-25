package com.arrheniq.kernel.security;

import java.util.Optional;

/** Request-scoped holder for {@link CurrentUser}; bound and cleared by {@link SessionContextFilter}. */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> CURRENT = new ThreadLocal<>();

    private CurrentUserHolder() { }

    public static Optional<CurrentUser> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static CurrentUser require() {
        CurrentUser u = CURRENT.get();
        if (u == null) {
            throw new IllegalStateException("ARQ-AUTH-001: no authenticated user bound to this thread");
        }
        return u;
    }

    static void set(CurrentUser user) {
        CURRENT.set(user);
    }

    static void clear() {
        CURRENT.remove();
    }
}
