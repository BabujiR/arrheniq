/**
 * Master data: test library and products (URS-MD-001, URS-MD-002), later specifications, dictionaries,
 * formulations (docs/architecture/bounded-contexts.md). Every write is permission- and site-checked,
 * versioned (optimistic locking) and audited field by field in the same transaction.
 */
@org.jspecify.annotations.NullMarked
@org.springframework.modulith.ApplicationModule(displayName = "Master data")
package com.arrheniq.masterdata;
