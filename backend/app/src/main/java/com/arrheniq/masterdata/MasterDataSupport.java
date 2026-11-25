package com.arrheniq.masterdata;

import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.security.CurrentUser;
import com.arrheniq.kernel.security.CurrentUserHolder;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Helpers shared by the master-data services. */
final class MasterDataSupport {

    static final int MAX_PAGE = 200;

    private MasterDataSupport() { }

    static AuditActor actor() {
        CurrentUser u = CurrentUserHolder.require();
        return new AuditActor(u.userId(), u.displayName(), AuditActor.Type.USER);
    }

    /** Escapes LIKE wildcards; the query uses {@code ESCAPE '\'}. */
    static String likePattern(String q) {
        return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    static int limit(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE));
    }

    static @Nullable Instant instant(@Nullable OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
