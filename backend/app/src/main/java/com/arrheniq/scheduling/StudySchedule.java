package com.arrheniq.scheduling;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A study's stored schedule; empty until the study is approved. */
public record StudySchedule(UUID studyId, @Nullable String algorithm, List<StudyTimepointView> timepoints) { }
