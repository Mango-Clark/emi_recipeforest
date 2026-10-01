package io.github.mango_clark.emirecipeforest.forest;

import java.math.BigDecimal;
import java.math.RoundingMode;

import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.TreeCost;

/** Checked quantity operations at forest input and display boundaries. */
public final class ForestAmounts {
    private ForestAmounts() {
    }

    /**
     * Divides a nonnegative quantity, rounding upward without a floating point conversion.
     * @param amount nonnegative quantity
     * @param divisor positive output quantity
     * @return rounded quotient
     */
    public static long ceilDiv(long amount, long divisor) {
        if (amount < 0 || divisor <= 0) {
            throw new ArithmeticException("Invalid recipe quantity");
        }
        return amount / divisor + (amount % divisor == 0 ? 0 : 1);
    }

    /**
     * Applies EMI's batch input conventions, rejecting an overflowing increment.
     * @param current current positive batch count
     * @param delta input adjustment
     * @param shift whether the sixteen-batch modifier is active
     * @param ctrl whether the doubling/halving modifier is active
     * @return adjusted count, with the usual lower limit of one
     */
    public static long adjustBatch(long current, long delta, boolean shift, boolean ctrl) {
        if (current < 1) {
            throw new ArithmeticException("Invalid batch count");
        }
        long adjustment = shift ? Math.multiplyExact(delta, 16)
                : ctrl && delta != 0 ? delta > 0 ? current : -(current / 2) : delta;
        if (current == 1 && adjustment > 1) {
            return adjustment;
        }
        return Math.max(1, Math.addExact(current, adjustment));
    }

    /**
     * Rounds an expected quantity using long arithmetic instead of Math.round(float).
     * @param amount nonnegative quantity
     * @param chance finite, nonnegative probability multiplier
     * @return nearest representable long quantity
     */
    public static long roundExpected(long amount, float chance) {
        return roundExpected(BigDecimal.valueOf(amount).multiply(decimalChance(chance)));
    }

    static BigDecimal decimalChance(float chance) {
        if (!Float.isFinite(chance) || chance < 0) {
            throw new ArithmeticException("Invalid recipe chance");
        }
        // A float is exactly representable as a double; preserve its actual value.
        return new BigDecimal((double) chance);
    }

    static BigDecimal checkExpected(BigDecimal expected) {
        if (expected.signum() < 0 || expected.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Recipe quantity exceeds long range");
        }
        return expected;
    }

    static long roundExpected(BigDecimal expected) {
        return checkExpected(expected).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Uses EMI's ideal-batch algorithm with checked intermediate products.
     * @param node root node
     * @param cost native EMI cost helper
     * @return ideal batch count
     */
    public static long idealBatch(MaterialNode node, TreeCost cost) {
        return idealBatch(node, cost, 1, 1);
    }

    private static long idealBatch(MaterialNode node, TreeCost cost, long total, long amount) {
        if (node.recipe == null) {
            return total;
        }
        if (node.divisor > 0) {
            long mod = node.divisor / cost.gcd(node.divisor, amount);
            total = Math.multiplyExact(total, mod / cost.gcd(total, mod));
        }
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                total = idealBatch(child, cost, total, Math.multiplyExact(amount, child.amount));
            }
        }
        return total;
    }
}
