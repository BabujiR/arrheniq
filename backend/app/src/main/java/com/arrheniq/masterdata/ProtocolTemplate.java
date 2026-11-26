package com.arrheniq.masterdata;

import java.math.BigDecimal;
import java.util.List;

/**
 * A standard time-point plan shipped with ArrheniQ (URS-MD-007), e.g. long-term 25 °C/60 % RH over 36 months
 * per ICH Q1A(R2). Read-only; the protocol builder (S1.5) copies one into an editable protocol.
 *
 * @param conditionLabel the tenant's label of the storage condition (may have been renamed)
 * @param source         guideline reference
 */
public record ProtocolTemplate(String code, String name, String studyType, String conditionCode,
                               String conditionLabel, String timepointUnit, List<BigDecimal> timepoints,
                               String source, String note) {
}
