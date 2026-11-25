package com.arrheniq.kernel.security;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the tenant's session times (tenant settings {@code session.idle_lock_minutes} and
 * {@code session.logoff_minutes}, seeded by tenant provisioning) with safe bounds.
 */
@Component
public class SessionSettings {

    public record Times(Duration idleLock, Duration logoff) { }

    static final Duration MIN = Duration.ofMinutes(1);
    static final Duration MAX = Duration.ofHours(8);

    private final JdbcClient jdbc;
    private final ArrheniqSecurityProperties props;

    public SessionSettings(JdbcClient jdbc, ArrheniqSecurityProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Times forCurrentTenant() {
        Map<String, String> values = new HashMap<>();
        jdbc.sql("""
                SELECT setting_key, value::text FROM app.tenant_setting
                 WHERE site_id IS NULL AND deleted_at IS NULL
                   AND setting_key IN ('session.idle_lock_minutes', 'session.logoff_minutes')
                """)
            .query(rs -> {
                values.put(rs.getString(1), rs.getString(2));
            });
        return resolve(values.get("session.idle_lock_minutes"), values.get("session.logoff_minutes"),
                props.defaultIdleLock(), props.defaultLogoff());
    }

    /** Pure resolution with bounds: 1 min ≤ idle lock ≤ logoff ≤ 8 h. */
    static Times resolve(@Nullable String idleLockMinutes, @Nullable String logoffMinutes, Duration defaultIdle,
                         Duration defaultLogoff) {
        Duration idle = clamp(parseMinutes(idleLockMinutes, defaultIdle));
        Duration logoff = clamp(parseMinutes(logoffMinutes, defaultLogoff));
        if (logoff.compareTo(idle) < 0) {
            logoff = idle;
        }
        return new Times(idle, logoff);
    }

    private static Duration parseMinutes(@Nullable String json, Duration fallback) {
        if (json == null) {
            return fallback;
        }
        try {
            return Duration.ofMinutes(Long.parseLong(json.trim().replace("\"", "")));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Duration clamp(Duration d) {
        return d.compareTo(MIN) < 0 ? MIN : d.compareTo(MAX) > 0 ? MAX : d;
    }
}
