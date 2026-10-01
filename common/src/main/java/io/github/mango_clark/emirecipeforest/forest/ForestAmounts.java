package io.github.mango_clark.emirecipeforest.forest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
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
        return roundExpected(Fraction.of(amount).multiply(Fraction.ofChance(chance)));
    }

    /**
     * Rounds with the same exact probability multiplier used by forest costs.
     * @param amount nonnegative quantity
     * @param chance probability operations along the recipe path
     * @return nearest representable long quantity
     */
    public static long roundExpected(long amount, Chance chance) {
        return roundExpected(Fraction.of(amount).multiply(chance.value));
    }

    static Fraction checkExpected(Fraction expected) {
        if (expected.signum() < 0 || expected.compareTo(Fraction.of(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("Recipe quantity exceeds long range");
        }
        return expected;
    }

    static long roundExpected(Fraction expected) {
        return checkExpected(expected).roundLong(RoundingMode.HALF_UP);
    }

    /**
     * Exact arithmetic adapter for EMI's output-division and input-multiplication rules.
     * A path remains chanced even when its probability factors cancel.
     */
    public static final class Chance {
        public static final Chance DEFAULT = new Chance(Fraction.ONE, false);
        final Fraction value;
        final boolean chanced;

        Chance(Fraction value, boolean chanced) {
            this.value = value;
            this.chanced = chanced;
        }

        /** @return whether the path contains a non-unit probability */
        public boolean chanced() {
            return chanced;
        }

        /**
         * @param chance output probability
         * @return path multiplier divided by the output probability
         */
        public Chance produce(float chance) {
            if (chance == 1) {
                return this;
            }
            Fraction divisor = Fraction.ofChance(chance);
            if (divisor.signum() == 0) {
                throw new ArithmeticException("Invalid zero output chance");
            }
            return new Chance(value.divide(divisor), true);
        }

        /**
         * @param chance input consumption probability
         * @return path multiplier multiplied by the consumption probability
         */
        public Chance consume(float chance) {
            return chance == 1 ? this : new Chance(value.multiply(Fraction.ofChance(chance)), true);
        }
    }

    // Only final integer conversion and EMI's float presentation fields are rounded.
    static record Fraction(BigInteger numerator, BigInteger denominator) implements Comparable<Fraction> {
        static final Fraction ZERO = of(0);
        static final Fraction ONE = of(1);

        Fraction {
            if (denominator.signum() == 0) {
                throw new ArithmeticException("Zero quantity denominator");
            }
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        static Fraction of(long value) {
            return new Fraction(BigInteger.valueOf(value), BigInteger.ONE);
        }

        static Fraction ofChance(float chance) {
            if (!Float.isFinite(chance) || chance < 0) {
                throw new ArithmeticException("Invalid recipe chance");
            }
            // A float is exactly representable as a double; preserve its actual value.
            BigDecimal value = new BigDecimal((double) chance);
            return new Fraction(value.unscaledValue(), BigInteger.TEN.pow(value.scale()));
        }

        Fraction add(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Fraction subtract(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Fraction multiply(Fraction other) {
            return new Fraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        Fraction divide(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
        }

        Fraction min(Fraction other) {
            return compareTo(other) <= 0 ? this : other;
        }

        Fraction max(Fraction other) {
            return compareTo(other) >= 0 ? this : other;
        }

        int signum() {
            return numerator.signum();
        }

        @Override
        public int compareTo(Fraction other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }

        long roundLong(RoundingMode rounding) {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), 0, rounding).longValueExact();
        }

        float floatValue() {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL128).floatValue();
        }
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
