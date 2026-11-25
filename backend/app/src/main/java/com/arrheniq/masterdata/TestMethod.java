package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A library test as returned by the API. */
public record TestMethod(UUID id, long version, String code, String name, @Nullable String methodReference,
                         @Nullable String labNumber, String resultsFormat, @Nullable String unitCode, int replicates,
                         @Nullable Integer decimals, @Nullable BigDecimal defaultLowerLimit,
                         @Nullable BigDecimal defaultUpperLimit, @Nullable String defaultTextLimit,
                         @Nullable BigDecimal defaultAlertLower, @Nullable BigDecimal defaultAlertUpper,
                         @Nullable BigDecimal defaultVarianceLowerPct, @Nullable BigDecimal defaultVarianceUpperPct,
                         boolean trendable, String status, Instant createdAt, @Nullable Instant updatedAt) {

    /** Fields compared for the audit trail (business fields + status). */
    Map<String, @Nullable Object> auditMap() {
        return auditMap(new TestMethodInput.Clean(code, name, methodReference, labNumber, resultsFormat, unitCode,
                replicates, decimals, defaultLowerLimit, defaultUpperLimit, defaultTextLimit, defaultAlertLower,
                defaultAlertUpper, defaultVarianceLowerPct, defaultVarianceUpperPct, trendable), status);
    }

    static Map<String, @Nullable Object> auditMap(TestMethodInput.Clean c, String status) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("code", c.code());
        m.put("name", c.name());
        m.put("methodReference", c.methodReference());
        m.put("labNumber", c.labNumber());
        m.put("resultsFormat", c.resultsFormat());
        m.put("unitCode", c.unitCode());
        m.put("replicates", (long) c.replicates());
        m.put("decimals", c.decimals() == null ? null : (long) c.decimals());
        m.put("defaultLowerLimit", c.defaultLowerLimit());
        m.put("defaultUpperLimit", c.defaultUpperLimit());
        m.put("defaultTextLimit", c.defaultTextLimit());
        m.put("defaultAlertLower", c.defaultAlertLower());
        m.put("defaultAlertUpper", c.defaultAlertUpper());
        m.put("defaultVarianceLowerPct", c.defaultVarianceLowerPct());
        m.put("defaultVarianceUpperPct", c.defaultVarianceUpperPct());
        m.put("trendable", c.trendable());
        m.put("status", status);
        return m;
    }
}
