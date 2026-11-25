package com.arrheniq;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies module boundaries (ADR 0001): no cycles, and modules only use other modules' public API.
 * Runs as a fast unit test on every build.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(ArrheniqApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        modules.verify();
    }
}
