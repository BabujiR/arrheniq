package com.arrheniq.masterdata;

import com.arrheniq.kernel.audit.AuditActor;
import com.arrheniq.kernel.security.CurrentUser;
import com.arrheniq.kernel.security.CurrentUserHolder;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

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

    /** Plain JSON (exact decimals), for jsonb columns read back as text. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };

    /** Parses a JSON object stored in a jsonb column (e.g. list-value attributes). */
    static Map<String, Object> jsonObject(String json) {
        return JSON.readValue(json, OBJECT);
    }
}
