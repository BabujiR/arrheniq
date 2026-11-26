package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A test of a specification version with its product-specific limits (URS-MD-003).
 *
 * @param testCode   code of the library test (shown and used as the key in the audit trail)
 * @param testStatus ACTIVE or RETIRED in the library (a retired test stays in versions that already had it)
 */
public record SpecTest(UUID testMethodId, String testCode, String testStatus, int sortOrder, String displayName,
                       String resultsFormat, @Nullable String unitCode, int replicates, @Nullable Integer decimals,
                       @Nullable BigDecimal lowerLimit, @Nullable BigDecimal upperLimit, @Nullable String textLimit,
                       @Nullable BigDecimal alertLower, @Nullable BigDecimal alertUpper, boolean trendable) {

    /** Business fields keyed {@code test.<CODE>.<field>}: compared for the audit trail and hashed for signatures. */
    void putContent(Map<String, @Nullable Object> m) {
        String p = "test." + testCode + ".";
        m.put(p + "sortOrder", (long) sortOrder);
        m.put(p + "displayName", displayName);
        m.put(p + "resultsFormat", resultsFormat);
        m.put(p + "unitCode", unitCode);
        m.put(p + "replicates", (long) replicates);
        m.put(p + "decimals", decimals == null ? null : (long) decimals);
        m.put(p + "lowerLimit", lowerLimit);
        m.put(p + "upperLimit", upperLimit);
        m.put(p + "textLimit", textLimit);
        m.put(p + "alertLower", alertLower);
        m.put(p + "alertUpper", alertUpper);
        m.put(p + "trendable", trendable);
    }

    static Map<String, @Nullable Object> content(Iterable<SpecTest> tests) {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        tests.forEach(t -> t.putContent(m));
        return m;
    }
}
