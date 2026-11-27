package com.arrheniq.study;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a study becomes APPROVED, inside the approving transaction. Scheduling generates the schedule in
 * the same transaction (URS-STU-005); if it can't, the approval rolls back.
 */
public record StudyApproved(UUID studyId, String studyCode, UUID siteId, UUID protocolVersionId,
                            LocalDate onStabilityDate) { }
