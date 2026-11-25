package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Set;

/** Validation of library tests (URS-MD-001; legacy BR-004..006). */
final class TestMethodRules {

    static final Set<String> RESULTS_FORMATS = Set.of("NUMERIC", "TEXT");

    private TestMethodRules() { }

    static TestMethodInput.Clean validate(TestMethodInput in) {
        Validation v = new Validation();
        String code = Validation.normalizeCode(in.code());
        v.code("code", code);
        String name = Validation.trimToNull(in.name());
        v.required("name", name, 200);
        v.maxLength("methodReference", in.methodReference(), 200);
        v.maxLength("labNumber", in.labNumber(), 50);
        String format = in.resultsFormat() == null ? null : in.resultsFormat().trim().toUpperCase(java.util.Locale.ROOT);
        v.oneOf("resultsFormat", format, RESULTS_FORMATS);
        int replicates = in.replicates() == null ? 1 : in.replicates();
        v.range("replicates", replicates, 1, 100);
        v.range("decimals", in.decimals(), 0, 10);
        String unit = Validation.trimToNull(in.unitCode());
        String textLimit = Validation.trimToNull(in.defaultTextLimit());

        if ("TEXT".equals(format)) {
            String why = "numeric attributes are not used by TEXT tests";
            v.absent("unitCode", unit, why);
            v.absent("decimals", in.decimals(), why);
            v.absent("defaultLowerLimit", in.defaultLowerLimit(), why);
            v.absent("defaultUpperLimit", in.defaultUpperLimit(), why);
            v.absent("defaultAlertLower", in.defaultAlertLower(), why);
            v.absent("defaultAlertUpper", in.defaultAlertUpper(), why);
            v.absent("defaultVarianceLowerPct", in.defaultVarianceLowerPct(), why);
            v.absent("defaultVarianceUpperPct", in.defaultVarianceUpperPct(), why);
            v.maxLength("defaultTextLimit", textLimit, 500);
        } else if ("NUMERIC".equals(format)) {
            v.absent("defaultTextLimit", textLimit, "a text limit is used by TEXT tests only");
            v.ordered("defaultLowerLimit", in.defaultLowerLimit(), in.defaultUpperLimit());
            v.ordered("defaultAlertLower", in.defaultAlertLower(), in.defaultAlertUpper());
            v.notNegative("defaultVarianceLowerPct", in.defaultVarianceLowerPct());
            v.notNegative("defaultVarianceUpperPct", in.defaultVarianceUpperPct());
            v.maxLength("unitCode", unit, 40);
        }
        v.throwIfAny();
        return new TestMethodInput.Clean(code, name, Validation.trimToNull(in.methodReference()),
                Validation.trimToNull(in.labNumber()), format, unit, replicates, in.decimals(),
                strip(in.defaultLowerLimit()), strip(in.defaultUpperLimit()), textLimit,
                strip(in.defaultAlertLower()), strip(in.defaultAlertUpper()),
                strip(in.defaultVarianceLowerPct()), strip(in.defaultVarianceUpperPct()),
                in.trendable() == null || in.trendable());
    }

    private static @Nullable BigDecimal strip(@Nullable BigDecimal d) {
        return d == null ? null : d.stripTrailingZeros();
    }
}
