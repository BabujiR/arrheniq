package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request body of a protocol draft save: the full content (replaces the previous one). */
public record ProtocolInput(@Nullable String studyType, @Nullable String conditionCode, @Nullable Integer reserveUnits,
                            @Nullable String fillUnitCode, @Nullable String changeSummary,
                            @Nullable List<TimePointInput> timepoints, @Nullable List<TestInput> tests) {

    public record TimePointInput(@Nullable BigDecimal value, @Nullable String unit, @Nullable String label,
                                 @Nullable Integer unitsPlanned) { }

    /** @param at indexes into {@code timepoints} (as sent) where this test runs */
    public record TestInput(@Nullable UUID testMethodId, @Nullable List<Integer> at) { }
}
