/**
 * Shared kernel: tenancy context, audit and e-signature kernel, authorization, configuration
 * (docs/architecture/bounded-contexts.md §2). Declared OPEN so domain modules may use its types.
 * Keep it small and stable.
 */
@ApplicationModule(displayName = "Kernel", type = ApplicationModule.Type.OPEN)
@NullMarked
package com.arrheniq.kernel;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
