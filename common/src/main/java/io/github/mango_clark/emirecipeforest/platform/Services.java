package io.github.mango_clark.emirecipeforest.platform;

import io.github.mango_clark.emirecipeforest.Constants;
import io.github.mango_clark.emirecipeforest.platform.services.IPlatformHelper;

import java.util.Optional;
import java.util.ServiceLoader;

/** Resolves loader-specific services without exposing loader APIs to common code. */
public class Services {
    /** Platform service supplied by the active loader module. */
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    /**
     * Returns the active loader's declared version for a loaded mod.
     *
     * @param modId mod identifier to query
     * @return declared version, or empty when the mod is not loaded
     */
    public static Optional<String> getModVersion(String modId) {
        return PLATFORM.getModVersion(modId);
    }

    /**
     * Loads the first implementation declared through {@link ServiceLoader}.
     *
     * @param clazz service interface
     * @param <T> service type
     * @return loader-specific implementation
     * @throws NullPointerException when no provider is declared
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz).findFirst().orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
