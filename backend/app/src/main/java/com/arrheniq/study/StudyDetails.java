package com.arrheniq.study;

import com.arrheniq.kernel.esign.ApprovalService;
import com.arrheniq.kernel.esign.Signature;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One study with everything the study page shows except its schedule (scheduling module). */
public record StudyDetails(StudySummary study, long version, @Nullable LocalDate manufactureDate,
                           @Nullable LocalDate expiryDate, String reasonCode, String reasonLabel,
                           String studyLengthCode, String studyLengthLabel, String storageLocationCode,
                           String storageLocationLabel, @Nullable String proprietaryStudyId, @Nullable String notes,
                           int unitsPlaced, int reserveUnits, @Nullable String fillUnitCode,
                           @Nullable String scheduleAlgorithm, @Nullable Instant approvedAt, List<Sibling> siblings,
                           List<Signature> signatures, ApprovalService.Progress approval, Instant createdAt,
                           @Nullable Instant updatedAt) {

    /** Another study of the same login (same lot, other condition). */
    public record Sibling(UUID id, String studyCode, String conditionCode, String conditionLabel, String status) { }
}
