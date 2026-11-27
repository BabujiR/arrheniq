package com.arrheniq.scheduling;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads a site's {@link SchedulingRules} and pull window: the tenant settings {@code scheduling.saturday_rule},
 * {@code scheduling.sunday_rule} and {@code scheduling.holiday_rule} (a site-level row wins over the tenant
 * default) and the site's {@code holiday_calendar}. A missing or unreadable value is a configuration error, not
 * a silent default (ADR 0011). Runs inside the caller's transaction and tenant context.
 */
@Component
class SchedulingSettings {

    static final String SATURDAY = "scheduling.saturday_rule";
    static final String SUNDAY = "scheduling.sunday_rule";
    static final String HOLIDAY = "scheduling.holiday_rule";
    static final String WINDOW = "scheduling.pull_window_days";

    private final JdbcClient jdbc;

    SchedulingSettings(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    SchedulingRules forSite(UUID siteId) {
        Map<String, String> tenant = new HashMap<>();
        Map<String, String> site = new HashMap<>();
        jdbc.sql("""
                SELECT setting_key, value #>> '{}' AS v, site_id IS NOT NULL AS site_level
                  FROM app.tenant_setting
                 WHERE setting_key IN (?, ?, ?) AND deleted_at IS NULL AND (site_id IS NULL OR site_id = ?)
                """)
            .params(SATURDAY, SUNDAY, HOLIDAY, siteId)
            .query(rs -> {
                (rs.getBoolean("site_level") ? site : tenant).put(rs.getString("setting_key"), rs.getString("v"));
            });
        List<String> calendar = jdbc.sql("""
                SELECT d FROM app.site s, jsonb_array_elements_text(s.holiday_calendar) AS d
                 WHERE s.id = ? AND s.deleted_at IS NULL
                """)
            .param(siteId)
            .query((rs, _) -> rs.getString(1))
            .list();
        Set<LocalDate> holidays = new HashSet<>();
        for (String d : calendar) {
            try {
                holidays.add(LocalDate.parse(d));
            } catch (DateTimeParseException e) {
                throw new IllegalStateException("site " + siteId + " has an invalid holiday '" + d + "'", e);
            }
        }
        return new SchedulingRules(rule(SATURDAY, site, tenant), rule(SUNDAY, site, tenant),
                rule(HOLIDAY, site, tenant), holidays);
    }

    /** {@code scheduling.pull_window_days}: a pull is on time within ± this many days (BR-036). */
    int windowDays(UUID siteId) {
        String value = jdbc.sql("""
                SELECT value #>> '{}' FROM app.tenant_setting
                 WHERE setting_key = ? AND deleted_at IS NULL AND (site_id IS NULL OR site_id = ?)
                 ORDER BY site_id NULLS LAST LIMIT 1
                """)
            .params(WINDOW, siteId).query(String.class).optional()
            .orElseThrow(() -> new IllegalStateException("tenant setting " + WINDOW + " is missing"));
        try {
            int days = Integer.parseInt(value.trim());
            if (days < 0 || days > 60) {
                throw new NumberFormatException("outside 0..60");
            }
            return days;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("tenant setting " + WINDOW + " must be 0..60 days: '" + value + "'", e);
        }
    }

    private static DateRule rule(String key, Map<String, String> site, Map<String, String> tenant) {
        String value = site.containsKey(key) ? site.get(key) : tenant.get(key);
        if (value == null) {
            throw new IllegalStateException("tenant setting " + key + " is missing");
        }
        try {
            return DateRule.parse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("tenant setting " + key + ": " + e.getMessage(), e);
        }
    }
}
