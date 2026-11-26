package com.arrheniq.kernel.esign;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads the configured {@link ApprovalLevel} for a kind of record: tenant setting {@code approval.<kind>}, a
 * site-specific value overriding the tenant default (V11 seeds product, specification, protocol, study, results).
 */
@Component
public class ApprovalPolicy {

    private static final Pattern KIND = Pattern.compile("[a-z][a-z_]{1,39}");

    private final JdbcClient jdbc;

    public ApprovalPolicy(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public ApprovalLevel levelFor(String kind, @Nullable UUID siteId) {
        if (!KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("invalid approval kind");
        }
        String value = jdbc.sql("""
                SELECT value::text FROM app.tenant_setting
                 WHERE setting_key = ? AND deleted_at IS NULL AND (site_id IS NULL OR site_id = ?)
                 ORDER BY site_id NULLS LAST
                 LIMIT 1
                """)
            // as a list: siteId may be NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList("approval." + kind, siteId))
            .query(String.class)
            .optional()
            .orElse(null);
        return ApprovalLevel.parse(value);
    }
}
