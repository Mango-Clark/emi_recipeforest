package io.github.mango_clark.emirecipeforest;

import io.github.mango_clark.emirecipeforest.compat.EmiCompatibility;

/** Loader-neutral client initialization shared by every supported platform. */
public final class CommonClass {
    private static boolean initialized;

    private CommonClass() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        EmiCompatibility.validateOrThrow();
        initialized = true;
    }
}
