package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One test of a specification draft, as sent by the editor. Unit, decimals, replicates and the display name
 * default to the library test; limits are product-specific (URS-MD-003): two-sided, one-sided (only
 * {@code lowerLimit}, e.g. NLT 80 %, or only {@code upperLimit}, e.g. NMT 0.5 %), none (report only), or a text
 * limit for TEXT tests.
 */
public record SpecTestInput(@Nullable UUID testMethodId, @Nullable String displayName, @Nullable String unitCode,
                            @Nullable Integer replicates, @Nullable Integer decimals,
                            @Nullable BigDecimal lowerLimit, @Nullable BigDecimal upperLimit,
                            @Nullable String textLimit, @Nullable BigDecimal alertLower,
                            @Nullable BigDecimal alertUpper, @Nullable Boolean trendable) {
}
