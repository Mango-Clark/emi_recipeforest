package io.github.mango_clark.emirecipeforest.screen;

/** Horizontal extents in the forest's unscaled, centered render coordinates. */
public final class ForestViewport {
    private double left;
    private double right;

    /** Creates extents containing the forest origin. */
    public ForestViewport() {
    }

    /**
     * Includes an element's actual render range, after layout offsets.
     * @param elementLeft left edge in forest coordinates
     * @param elementRight right edge in forest coordinates
     */
    public void include(double elementLeft, double elementRight) {
        left = Math.min(left, elementLeft);
        right = Math.max(right, elementRight);
    }

    /**
     * Allows panning to either content edge while retaining EMI's 100-unit inset.
     * Shrinks the inset for narrow viewports so the limits cannot invert.
     * @param offset requested horizontal translation in forest coordinates
     * @param viewportLeft visible left edge before translation
     * @param viewportRight visible right edge before translation
     * @return bounded translation
     */
    public double clampOffset(double offset, double viewportLeft, double viewportRight) {
        double inset = Math.min(100, Math.max(0, viewportRight - viewportLeft) / 2);
        double minimum = Math.min(0, viewportLeft + inset - right);
        double maximum = Math.max(0, viewportRight - inset - left);
        return Math.max(minimum, Math.min(offset, maximum));
    }
}
