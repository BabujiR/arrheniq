/**
 * Studies: study login (lot × condition), study IDs, the study workflow and, later, packaging components, raw
 * materials, attachments, back-entry and termination (URS-STU-*; docs/architecture/bounded-contexts.md). Approving
 * a study publishes {@link com.arrheniq.study.StudyApproved}; scheduling generates the schedule in the same
 * transaction.
 */
@org.jspecify.annotations.NullMarked
@org.springframework.modulith.ApplicationModule(displayName = "Studies")
package com.arrheniq.study;
