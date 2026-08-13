package io.github.mango_clark.emirecipeforest.forest;

import java.util.Objects;

/**
 * Overflow-safe decomposition of an amount into box, stack, and item display units.
 *
 * @param boxes complete boxes
 * @param fullStacks complete stacks outside boxes
 * @param items loose items outside complete stacks
 * @param stackUnitCapacity items represented by one stack
 * @param boxUnitCapacity items represented by one box, saturated at {@link Long#MAX_VALUE}
 * @param mode presentation selected by the user
 */
public record QuantityDisplay(long boxes, long fullStacks, long items, long stackUnitCapacity,
        long boxUnitCapacity, DisplayMode mode) {
    /** Available quantity presentation styles. */
    public enum DisplayMode {
        /** Box, stack, and item icons. */
        ICON,
        /** Compact textual quantities. */
        TEXT
    }

    /**
     * Decomposes a non-negative amount without overflowing box capacity.
     *
     * @param total total item count
     * @param maxStack items per stack
     * @param boxEnabled whether complete boxes should be extracted
     * @param stacksPerBox stacks represented by one box
     * @param mode quantity presentation
     * @return decomposed quantity
     * @throws IllegalArgumentException when an amount or capacity is outside its valid range
     * @throws NullPointerException when {@code mode} is null
     */
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

    /** Reports whether complete boxes are present.
     * @return whether at least one complete box is present
     */
    public boolean hasBoxes() {
        return boxes != 0;
    }

    /** Reports whether complete stacks are present.
     * @return whether at least one complete stack is present
     */
    public boolean hasFullStacks() {
        return fullStacks != 0;
    }

    /** Reports whether loose items are present.
     * @return whether at least one loose item is present
     */
    public boolean hasItems() {
        return items != 0;
    }

    private static long saturatedMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
