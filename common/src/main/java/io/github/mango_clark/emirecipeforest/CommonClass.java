package io.github.mango_clark.emirecipeforest;

import io.github.mango_clark.emirecipeforest.compat.EmiCompatibility;

/** Loader-neutral client initialization shared by every supported platform. */
public final class CommonClass {
    private static boolean initialized;

    private CommonClass() {
    }

    /**
     * Performs idempotent common initialization and rejects incompatible EMI runtimes.
     *
     * @throws IllegalStateException when the installed EMI version or bytecode contract is unsupported
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        EmiCompatibility.validateOrThrow();
        initialized = true;
    }
}
