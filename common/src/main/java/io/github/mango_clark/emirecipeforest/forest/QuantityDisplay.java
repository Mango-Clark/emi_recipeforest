package io.github.mango_clark.emirecipeforest.forest;

import java.util.Objects;

/** Overflow-safe decomposition of an amount into box, stack, and item display units. */
public record QuantityDisplay(long boxes, long fullStacks, long items, long stackUnitCapacity,
        long boxUnitCapacity, DisplayMode mode) {
    public enum DisplayMode {
        ICON,
        TEXT
    }

    public static QuantityDisplay decompose(long total, long maxStack, boolean boxEnabled,
            long stacksPerBox, DisplayMode mode) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
        if (maxStack < 1) {
            throw new IllegalArgumentException("maxStack must be positive");
        }
        if (stacksPerBox < 1) {
            throw new IllegalArgumentException("stacksPerBox must be positive");
        }

        long stackCount = total / maxStack;
        long items = total % maxStack;
        long boxes = boxEnabled ? stackCount / stacksPerBox : 0;
        long fullStacks = boxEnabled ? stackCount % stacksPerBox : stackCount;
        return new QuantityDisplay(boxes, fullStacks, items, maxStack,
                saturatedMultiply(maxStack, stacksPerBox), Objects.requireNonNull(mode, "mode"));
    }

    public boolean hasBoxes() {
        return boxes != 0;
    }

    public boolean hasFullStacks() {
        return fullStacks != 0;
    }

    public boolean hasItems() {
        return items != 0;
    }

    private static long saturatedMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
