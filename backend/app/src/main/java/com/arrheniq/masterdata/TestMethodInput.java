package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Create/update request for a library test (URS-MD-001; legacy EA_PRD_TESTS). Limits are defaults
 * copied into specifications; numeric attributes apply to NUMERIC tests only.
 */
public record TestMethodInput(
        @Nullable String code,
        @Nullable String name,
        @Nullable String methodReference,
        @Nullable String labNumber,
        @Nullable String resultsFormat,
        @Nullable String unitCode,
        @Nullable Integer replicates,
        @Nullable Integer decimals,
        @Nullable BigDecimal defaultLowerLimit,
        @Nullable BigDecimal defaultUpperLimit,
        @Nullable String defaultTextLimit,
        @Nullable BigDecimal defaultAlertLower,
        @Nullable BigDecimal defaultAlertUpper,
        @Nullable BigDecimal defaultVarianceLowerPct,
        @Nullable BigDecimal defaultVarianceUpperPct,
        @Nullable Boolean trendable) {

    /** The validated, normalised values that are stored. */
    record Clean(String code, String name, @Nullable String methodReference, @Nullable String labNumber,
                 String resultsFormat, @Nullable String unitCode, int replicates, @Nullable Integer decimals,
                 @Nullable BigDecimal defaultLowerLimit, @Nullable BigDecimal defaultUpperLimit,
                 @Nullable String defaultTextLimit, @Nullable BigDecimal defaultAlertLower,
                 @Nullable BigDecimal defaultAlertUpper, @Nullable BigDecimal defaultVarianceLowerPct,
                 @Nullable BigDecimal defaultVarianceUpperPct, boolean trendable) { }
}
