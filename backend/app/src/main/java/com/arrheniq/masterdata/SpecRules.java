package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validation of a specification draft (URS-MD-003). Pure, so it is unit-tested without a database.
 * Field errors are keyed {@code tests[i].field}.
 */
final class SpecRules {

    static final int MAX_TESTS = 200;

    /** The library test as needed for validation and defaults. */
    record LibraryTest(UUID id, String code, String name, String status, String resultsFormat,
                       @Nullable String unitCode, int replicates, @Nullable Integer decimals, boolean trendable) { }

    private SpecRules() { }

    /**
     * @param library      library tests referenced by the draft, by id
     * @param carriedOver  tests already in the version this draft was copied from (may stay even if retired since)
     */
    static List<SpecTest> validate(List<SpecTestInput> tests, Map<UUID, LibraryTest> library, Set<UUID> carriedOver) {
        Validation v = new Validation();
        if (tests.size() > MAX_TESTS) {
            v.add("tests", "TOO_MANY", "at most " + MAX_TESTS + " tests");
            v.throwIfAny();
        }
        Set<UUID> seen = new HashSet<>();
        List<SpecTest> out = new ArrayList<>();
        for (int i = 0; i < tests.size(); i++) {
            SpecTestInput in = tests.get(i);
            String f = "tests[" + i + "].";
            UUID methodId = in.testMethodId();
            LibraryTest lib = methodId == null ? null : library.get(methodId);
            if (lib == null) {
                v.add(f + "testMethodId", methodId == null ? "REQUIRED" : "UNKNOWN", "a test from the test library");
                continue;
            }
            if (!seen.add(lib.id())) {
                v.add(f + "testMethodId", "DUPLICATE", lib.code() + " is already in this specification");
                continue;
            }
            if ("RETIRED".equals(lib.status()) && !carriedOver.contains(lib.id())) {
                v.add(f + "testMethodId", "RETIRED", lib.code() + " is retired in the test library");
            }
            String name = Validation.trimToNull(in.displayName());
            v.maxLength(f + "displayName", name, 200);
            int replicates = in.replicates() == null ? lib.replicates() : in.replicates();
            v.range(f + "replicates", replicates, 1, 100);
            boolean numeric = "NUMERIC".equals(lib.resultsFormat());
            String unit;
            Integer decimals;
            if (numeric) {
                unit = in.unitCode() == null ? lib.unitCode() : Validation.trimToNull(in.unitCode());
                decimals = in.decimals() == null ? lib.decimals() : in.decimals();
                v.maxLength(f + "unitCode", unit, 40);
                v.range(f + "decimals", decimals, 0, 10);
                v.absent(f + "textLimit", Validation.trimToNull(in.textLimit()), "numeric tests have numeric limits");
                v.ordered(f + "lowerLimit", in.lowerLimit(), in.upperLimit());
                v.ordered(f + "alertLower", in.alertLower(), in.alertUpper());
                within(v, f + "alertLower", in.alertLower(), in.lowerLimit(), in.upperLimit());
                within(v, f + "alertUpper", in.alertUpper(), in.lowerLimit(), in.upperLimit());
            } else {
                unit = null;
                decimals = null;
                String why = "text tests have a text limit only";
                v.absent(f + "unitCode", Validation.trimToNull(in.unitCode()), why);
                v.absent(f + "decimals", in.decimals(), why);
                v.absent(f + "lowerLimit", in.lowerLimit(), why);
                v.absent(f + "upperLimit", in.upperLimit(), why);
                v.absent(f + "alertLower", in.alertLower(), why);
                v.absent(f + "alertUpper", in.alertUpper(), why);
                v.maxLength(f + "textLimit", in.textLimit(), 500);
            }
            out.add(new SpecTest(lib.id(), lib.code(), lib.status(), (i + 1) * 10,
                    name == null ? lib.name() : name, lib.resultsFormat(), unit, replicates, decimals,
                    numeric ? in.lowerLimit() : null, numeric ? in.upperLimit() : null,
                    numeric ? null : Validation.trimToNull(in.textLimit()),
                    numeric ? in.alertLower() : null, numeric ? in.alertUpper() : null,
                    in.trendable() == null ? lib.trendable() : in.trendable()));
        }
        v.throwIfAny();
        return out;
    }

    /** Alert limits lie inside the specification limits (they warn before a result fails). */
    private static void within(Validation v, String field, @Nullable BigDecimal alert, @Nullable BigDecimal lower,
                               @Nullable BigDecimal upper) {
        if (alert == null) {
            return;
        }
        if ((lower != null && alert.compareTo(lower) < 0) || (upper != null && alert.compareTo(upper) > 0)) {
            v.add(field, "OUTSIDE_LIMITS", "alert limits must lie within the specification limits");
        }
    }
}
