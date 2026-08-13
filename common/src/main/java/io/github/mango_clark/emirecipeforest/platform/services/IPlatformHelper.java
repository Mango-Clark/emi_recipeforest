package io.github.mango_clark.emirecipeforest.platform.services;

import java.util.Optional;

/** Loader-specific metadata queries exposed to loader-neutral common code. */
public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Gets the declared version of a loaded mod.
     *
     * @param modId The id of the mod whose version should be queried.
     * @return The declared version, or an empty optional when the mod is not loaded.
     */
    Optional<String> getModVersion(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }
}
