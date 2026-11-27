package com.arrheniq.study;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** Changes to a DRAFT study. Product, specification and protocol are fixed: log in again to change them. */
public record StudyUpdateInput(@Nullable Long expectedVersion, @Nullable String lotNumber,
                               @Nullable LocalDate manufactureDate, @Nullable LocalDate expiryDate,
                               @Nullable LocalDate onStabilityDate, @Nullable String reasonCode,
                               @Nullable String studyLengthCode, @Nullable String storageLocationCode,
                               @Nullable String proprietaryStudyId, @Nullable String notes) { }
