package io.github.mango_clark.emirecipeforest.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmiCompatibilityTest {
    @Test
    void acceptsSupportedBounds() {
        assertTrue(EmiCompatibility.isSupportedVersion("1.1.13+1.20.1"));
        assertTrue(EmiCompatibility.isSupportedVersion("1.1.24+1.20.1"));
        assertTrue(EmiCompatibility.isSupportedVersion("1.1.13+1.20.1+fabric"));
        assertTrue(EmiCompatibility.isSupportedVersion("1.1.24+1.20.1+forge"));
        assertTrue(EmiCompatibility.isSupportedVersion("1.1.24+1.20.1+fabric+build.1"));
    }

    @Test
    void rejectsVersionsOutsideSupportedPatches() {
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.12+1.20.1"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.25+1.20.1"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.12+1.20.1+fabric"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.25+1.20.1+forge"));
    }

    @Test
    void rejectsOtherMinecraftVersionsAndMalformedValues() {
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.24+1.21.1"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.24+1.21.1+fabric"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.24"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.24+1.20.1+"));
        assertFalse(EmiCompatibility.isSupportedVersion("1.1.24+1.20.1++fabric"));
        assertFalse(EmiCompatibility.isSupportedVersion("not-a-version"));
        assertFalse(EmiCompatibility.isSupportedVersion(null));
    }
}
