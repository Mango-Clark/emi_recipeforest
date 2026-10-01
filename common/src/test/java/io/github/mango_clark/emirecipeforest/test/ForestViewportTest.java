package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.mango_clark.emirecipeforest.screen.ForestViewport;

class ForestViewportTest {
    @Test
    void nodePixelWidthIsUsedOnce() {
        ForestViewport bounds = new ForestViewport();
        bounds.include(-21, 21);
        assertEquals(-121, bounds.clampOffset(-10_000, -200, 200));
        assertEquals(121, bounds.clampOffset(10_000, -200, 200));
        assertEquals(0, bounds.clampOffset(0, -200, 200));
    }

    @Test
    void widerCostAndRemainderRowsExtendBothLimits() {
        ForestViewport bounds = new ForestViewport();
        bounds.include(-21, 21); // centered node
        bounds.include(-500, 400); // laid out total costs
        bounds.include(-300, 750); // laid out remainders, including amount labels
        assertEquals(-850, bounds.clampOffset(-10_000, -200, 200));
        assertEquals(600, bounds.clampOffset(10_000, -200, 200));
        // Both extreme edges can be brought into the visible viewport.
        assertEquals(-100, 750 + bounds.clampOffset(-10_000, -200, 200));
        assertEquals(100, -500 + bounds.clampOffset(10_000, -200, 200));
    }

    @Test
    void asymmetricCoordinatesAndZoomUseTheSameCoordinateSystem() {
        ForestViewport bounds = new ForestViewport();
        bounds.include(-120, 480);
        double center = 150;
        double panelLeft = 301;
        double scale = 0.5;
        assertEquals(-680, bounds.clampOffset(-10_000, -center / scale, (panelLeft - center) / scale));
        assertEquals(322, bounds.clampOffset(10_000, -center / scale, (panelLeft - center) / scale));
    }

    @Test
    void narrowAndZeroWidthViewportsNeverInvertTheLimits() {
        ForestViewport bounds = new ForestViewport();
        bounds.include(-16, 48);
        assertEquals(-48, bounds.clampOffset(-10_000, -20, 20));
        assertEquals(16, bounds.clampOffset(10_000, -20, 20));
        assertEquals(0, bounds.clampOffset(0, -20, 20));
        assertTrue(bounds.clampOffset(-10_000, 0, 0) <= bounds.clampOffset(10_000, 0, 0));
    }

    @Test
    void emptyForestAndRebuiltLayoutDoNotRetainOldExtents() {
        ForestViewport oldLayout = new ForestViewport();
        oldLayout.include(-2000, 2000);
        ForestViewport newLayout = new ForestViewport();
        assertEquals(-100, newLayout.clampOffset(-10_000, -200, 200));
        assertEquals(100, newLayout.clampOffset(10_000, -200, 200));
        assertEquals(0, newLayout.clampOffset(0, -20, 20));
    }
}
