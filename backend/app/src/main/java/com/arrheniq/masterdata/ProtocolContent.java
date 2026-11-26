package com.arrheniq.masterdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The content of a protocol version (URS-PRO-001/002/003/007): study type, intended storage condition, time
 * points with planned units, and which tests run at which time point.
 *
 * @param reserveUnits units kept in reserve for the whole study (re-tests, investigations)
 * @param fillUnitCode what a unit is (e.g. BOTTLE, BLISTER); free text up to 40 characters
 */
public record ProtocolContent(String studyType, @Nullable String conditionCode, int reserveUnits,
                              @Nullable String fillUnitCode, @Nullable String changeSummary,
                              List<TimePoint> timepoints, List<Test> tests) {

    /**
     * @param label         e.g. "Initial", "3M"
     * @param unitsPlanned  units pulled at this time point
     */
    public record TimePoint(BigDecimal value, String unit, String label, int unitsPlanned) {
        String key() {
            return value.stripTrailingZeros().toPlainString() + unit.charAt(0);
        }
    }

    /**
     * @param at indexes into {@link ProtocolContent#timepoints()}: the time points this test runs at
     */
    public record Test(UUID testMethodId, String testCode, String testName, String testStatus, String resultsFormat,
                       List<Integer> at) { }

    /** Planned units for the whole study: sum of all time points plus the reserve. */
    public int totalUnits() {
        return timepoints.stream().mapToInt(TimePoint::unitsPlanned).sum() + reserveUnits;
    }

    /** Fields compared for the audit trail and hashed for signatures, e.g. {@code tp.3M.unitsPlanned}, {@code grid.ASSAY}. */
    Map<String, @Nullable Object> auditMap() {
        Map<String, @Nullable Object> m = new LinkedHashMap<>();
        m.put("studyType", studyType);
        m.put("conditionCode", conditionCode);
        m.put("reserveUnits", (long) reserveUnits);
        m.put("fillUnitCode", fillUnitCode);
        m.put("changeSummary", changeSummary);
        for (TimePoint t : timepoints) {
            m.put("tp." + t.key() + ".label", t.label());
            m.put("tp." + t.key() + ".unitsPlanned", (long) t.unitsPlanned());
        }
        for (int i = 0; i < tests.size(); i++) {
            Test t = tests.get(i);
            m.put("test." + t.testCode() + ".order", (long) (i + 1));
            m.put("grid." + t.testCode(), String.join(" ",
                    t.at().stream().map(idx -> timepoints.get(idx).key()).toList()));
        }
        return m;
    }
}
