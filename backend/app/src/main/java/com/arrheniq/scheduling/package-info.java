/**
 * Scheduling: the pull-date engine (calendar arithmetic, weekend and site-holiday rules), later time points,
 * pull windows, pulls and the inventory ledger (URS-SCH-*, URS-INV-*; docs/architecture/bounded-contexts.md).
 */
@org.jspecify.annotations.NullMarked
@org.springframework.modulith.ApplicationModule(displayName = "Scheduling")
package com.arrheniq.scheduling;
