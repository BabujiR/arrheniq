package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Validation of a protocol draft (URS-PRO-001/002/003/007). Pure, unit-tested without a database. Time points are
 * put in chronological order (months counted as 30.4375 days) and the test grid follows them.
 */
final class ProtocolRules {

    static final Set<String> STUDY_TYPES = Set.of("LONG_TERM", "INTERMEDIATE", "ACCELERATED", "STRESS",
            "PHOTOSTABILITY", "IN_USE", "EXCURSION", "COMMITMENT", "ANNUAL", "OTHER");
    static final Set<String> UNITS = Set.of("DAY", "WEEK", "MONTH");
    static final int MAX_TIMEPOINTS = 100;
    static final int MAX_TESTS = 200;
    static final int MAX_UNITS = 100_000;

    private ProtocolRules() { }

    /**
     * @param library      library tests referenced by the draft, by id ({@link SpecRules.LibraryTest})
     * @param carriedOver  tests of the previous version (may stay even if retired in the library since)
     * @param conditions   codes of the active storage conditions
     */
    static ProtocolContent validate(ProtocolInput in, Map<UUID, SpecRules.LibraryTest> library, Set<UUID> carriedOver,
                                    Set<String> conditions) {
        Validation v = new Validation();
        String studyType = in.studyType() == null ? "" : in.studyType().trim().toUpperCase(Locale.ROOT);
        v.oneOf("studyType", studyType, STUDY_TYPES);
        String condition = Validation.trimToNull(in.conditionCode());
        if (condition != null && !conditions.contains(condition)) {
            v.add("conditionCode", "UNKNOWN", "an active storage condition from the lists");
        }
        int reserve = in.reserveUnits() == null ? 0 : in.reserveUnits();
        v.range("reserveUnits", reserve, 0, MAX_UNITS);
        String fillUnit = Validation.trimToNull(in.fillUnitCode());
        v.maxLength("fillUnitCode", fillUnit, 40);
        String summary = Validation.trimToNull(in.changeSummary());
        v.maxLength("changeSummary", summary, 2000);

        // ---- time points, in the order sent; then sorted chronologically
        List<ProtocolInput.TimePointInput> tpIn = in.timepoints() == null ? List.of() : in.timepoints();
        if (tpIn.size() > MAX_TIMEPOINTS) {
            v.add("timepoints", "TOO_MANY", "at most " + MAX_TIMEPOINTS + " time points");
            v.throwIfAny();
        }
        List<ProtocolContent.@Nullable TimePoint> sent = new ArrayList<>();   // null where invalid
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < tpIn.size(); i++) {
            ProtocolInput.TimePointInput t = tpIn.get(i);
            String f = "timepoints[" + i + "].";
            BigDecimal value = t.value();
            String unit = t.unit() == null ? "" : t.unit().trim().toUpperCase(Locale.ROOT);
            if (value == null) {
                v.add(f + "value", "REQUIRED", null);
            } else if (value.signum() < 0 || value.compareTo(new BigDecimal("10000")) > 0) {
                v.add(f + "value", "OUT_OF_RANGE", "0..10000");
            } else if (value.stripTrailingZeros().scale() > 0) {
                // pull dates are calendar arithmetic in whole months, weeks or days (URS-SCH-001)
                v.add(f + "value", "FORMAT", "a whole number");
            }
            v.oneOf(f + "unit", unit, UNITS);
            int units = t.unitsPlanned() == null ? 0 : t.unitsPlanned();
            v.range(f + "unitsPlanned", units, 0, MAX_UNITS);
            String label = Validation.trimToNull(t.label());
            v.maxLength(f + "label", label, 20);
            if (value != null && UNITS.contains(unit)) {
                ProtocolContent.TimePoint tp = new ProtocolContent.TimePoint(value, unit,
                        label == null ? defaultLabel(value, unit) : label, units);
                if (!keys.add(tp.key())) {
                    v.add(f + "value", "DUPLICATE", tp.key() + " appears twice");
                }
                sent.add(tp);
            } else {
                sent.add(null);
            }
        }

        // ---- tests and the grid (indexes refer to the time points as sent)
        List<ProtocolInput.TestInput> testIn = in.tests() == null ? List.of() : in.tests();
        if (testIn.size() > MAX_TESTS) {
            v.add("tests", "TOO_MANY", "at most " + MAX_TESTS + " tests");
        }
        Set<UUID> seen = new HashSet<>();
        record Pending(SpecRules.LibraryTest lib, Set<Integer> at) { }
        List<Pending> pending = new ArrayList<>();
        for (int i = 0; i < testIn.size(); i++) {
            ProtocolInput.TestInput t = testIn.get(i);
            String f = "tests[" + i + "].";
            UUID id = t.testMethodId();
            SpecRules.LibraryTest lib = id == null ? null : library.get(id);
            if (lib == null) {
                v.add(f + "testMethodId", id == null ? "REQUIRED" : "UNKNOWN", "a test from the test library");
                continue;
            }
            if (!seen.add(lib.id())) {
                v.add(f + "testMethodId", "DUPLICATE", lib.code() + " is already in this protocol");
                continue;
            }
            if ("RETIRED".equals(lib.status()) && !carriedOver.contains(lib.id())) {
                v.add(f + "testMethodId", "RETIRED", lib.code() + " is retired in the test library");
            }
            Set<Integer> at = new TreeSet<>(t.at() == null ? List.of() : t.at());
            for (int idx : at) {
                if (idx < 0 || idx >= tpIn.size()) {
                    v.add(f + "at", "INVALID_VALUE", "time point " + idx + " does not exist");
                }
            }
            if (at.isEmpty()) {
                v.add(f + "at", "REQUIRED", lib.code() + " runs at no time point");
            }
            pending.add(new Pending(lib, at));
        }
        v.throwIfAny();

        // ---- chronological order; remap the grid
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> days(sent.get(i))));
        Map<Integer, Integer> newIndex = new HashMap<>();
        List<ProtocolContent.TimePoint> timepoints = new ArrayList<>();
        for (int i : order) {
            newIndex.put(i, timepoints.size());
            timepoints.add(Objects.requireNonNull(sent.get(i)));   // no nulls left after validation
        }
        List<ProtocolContent.Test> tests = pending.stream()
                .map(p -> new ProtocolContent.Test(p.lib().id(), p.lib().code(), p.lib().name(), p.lib().status(),
                        p.lib().resultsFormat(), p.at().stream().map(newIndex::get).sorted().toList()))
                .toList();
        return new ProtocolContent(studyType, condition, reserve, fillUnit, summary, timepoints, tests);
    }

    /** "Initial" for 0, otherwise e.g. "3M", "2W", "7D", "1.5M". */
    static String defaultLabel(BigDecimal value, String unit) {
        if (value.signum() == 0) {
            return "Initial";
        }
        return value.stripTrailingZeros().toPlainString() + unit.charAt(0);
    }

    private static double days(ProtocolContent.@Nullable TimePoint t) {
        if (t == null) {
            return Double.MAX_VALUE;
        }
        double factor = switch (t.unit()) {
            case "DAY" -> 1;
            case "WEEK" -> 7;
            default -> 30.4375;
        };
        return t.value().doubleValue() * factor;
    }
}
