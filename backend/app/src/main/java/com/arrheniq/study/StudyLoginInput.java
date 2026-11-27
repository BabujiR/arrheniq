package com.arrheniq.study;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A study login (URS-STU-001/004): one lot of an approved product placed on stability under one or more storage
 * conditions. Each condition comes from an approved protocol of the product and gets its own study.
 */
public record StudyLoginInput(@Nullable UUID productId, @Nullable UUID specVersionId, @Nullable String lotNumber,
                              @Nullable LocalDate manufactureDate, @Nullable LocalDate expiryDate,
                              @Nullable LocalDate onStabilityDate, @Nullable String reasonCode,
                              @Nullable String studyLengthCode, @Nullable String proprietaryStudyId,
                              @Nullable String notes, @Nullable List<ConditionInput> conditions) {

    /** One storage condition: the approved protocol version that defines it, and where the samples are stored. */
    public record ConditionInput(@Nullable UUID protocolVersionId, @Nullable String storageLocationCode) { }
}
