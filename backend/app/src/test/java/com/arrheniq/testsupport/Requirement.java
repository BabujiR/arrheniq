package com.arrheniq.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a test to the URS requirement(s) it verifies (ADR 0015). The CI traceability step
 * (docs/ci/README.md, stage C7) collects these into the URS → test → result matrix.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Requirement {

    /** URS IDs, e.g. {@code "URS-AUD-003"}. */
    String[] value();
}
