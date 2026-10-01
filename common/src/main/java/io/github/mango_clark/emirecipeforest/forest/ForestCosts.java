package io.github.mango_clark.emirecipeforest.forest;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.ChanceMaterialCost;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.bom.ProgressState;
import dev.emi.emi.bom.TreeCost;
import io.github.mango_clark.emirecipeforest.compat.EmiCompatibility;
import io.github.mango_clark.emirecipeforest.forest.ForestAmounts.Chance;
import io.github.mango_clark.emirecipeforest.forest.ForestAmounts.Fraction;

/** Calculates forest-wide costs while carrying inventory and remainders between roots. */
public final class ForestCosts {
    private final TreeCost total = new TreeCost();
    private final TreeCost progress = new TreeCost();

    /** Creates an empty cost accumulator. */
    public ForestCosts() {
    }

    /** Returns aggregate cost without applying player inventory.
     * @return aggregate cost
     */
    public TreeCost getTotal() {
        return total;
    }

    /** Returns remaining cost after consuming the supplied player inventory.
     * @return remaining cost
     */
    public TreeCost getProgress() {
        return progress;
    }

    /**
     * Recalculates total and remaining costs for the ordered roots.
     * Inventory and recipe remainders are carried from each root into the next.
     *
     * @param trees ordered forest roots
     * @param inventory player inventory, or {@code null} for an empty inventory
     */
    public void calculate(List<MaterialTree> trees, EmiPlayerInventory inventory) {
        TreeCost nextProgress = new TreeCost();
        TreeCost nextTotal = new TreeCost();
        Accumulator progressAccumulator = withInventory(nextProgress, inventory);
        calculateTrees(progressAccumulator, trees, Map.of(), true);
        calculateTrees(new Accumulator(nextTotal), trees, Map.of(), false);
        // Publish only after both passes succeed, including deferred native node progress.
        copyCosts(nextTotal, total);
        copyCosts(nextProgress, progress);
        progressAccumulator.updates.forEach((node, update) -> {
            node.progress = update.progress;
            node.totalNeeded = update.totalNeeded;
            node.neededBatches = update.neededBatches;
        });
    }

    private static Accumulator withInventory(TreeCost nextProgress, EmiPlayerInventory inventory) {
        if (inventory != null) {
            for (EmiStack stack : inventory.inventory.values()) {
                EmiStack copy = stack.copy();
                nextProgress.remainders.put(copy, new FlatMaterialCost(copy, copy.getAmount()));
            }
        }
        return new Accumulator(nextProgress);
    }

    /**
     * Creates and calculates a fresh accumulator.
     *
     * @param trees ordered forest roots
     * @param inventory player inventory, or {@code null}
     * @return calculated forest costs
     */
    public static ForestCosts calculateNew(List<MaterialTree> trees, EmiPlayerInventory inventory) {
        ForestCosts costs = new ForestCosts();
        costs.calculate(trees, inventory);
        return costs;
    }

    /**
     * Checks candidate batch counts without changing costs or native node progress.
     * @param trees ordered forest roots
     * @param candidateBatches overrides for roots being changed
     * @throws ArithmeticException when a quantity cannot be represented
     */
    public static void validateAmounts(List<MaterialTree> trees, Map<MaterialTree, Long> candidateBatches) {
        calculateTrees(new Accumulator(new TreeCost()), trees, candidateBatches, false);
    }

    /** Checks inventory-dependent costs too, without publishing native progress.
     * @param trees ordered roots
     * @param candidateBatches proposed batches
     * @param inventory current inventory, or null
     */
    public static void validateAmounts(List<MaterialTree> trees, Map<MaterialTree, Long> candidateBatches,
            EmiPlayerInventory inventory) {
        validateAmounts(trees, candidateBatches);
        if (inventory != null) {
            calculateTrees(withInventory(new TreeCost(), inventory), trees, candidateBatches, false);
        }
    }

    private static void calculateTrees(Accumulator accumulator, List<MaterialTree> trees,
            Map<MaterialTree, Long> batches, boolean trackProgress) {
        for (MaterialTree tree : trees) {
            if (tree != null && tree.goal != null) {
                long count = batches.getOrDefault(tree, tree.batches);
                if (count < 1) {
                    throw new ArithmeticException("Invalid batch count");
                }
                accumulator.calculate(tree.goal, count, trackProgress);
            }
        }
    }

    private static void copyCosts(TreeCost source, TreeCost target) {
        target.costs.clear();
        target.costs.putAll(source.costs);
        target.chanceCosts.clear();
        target.chanceCosts.putAll(source.chanceCosts);
        target.remainders.clear();
        target.remainders.putAll(source.remainders);
        target.chanceRemainders.clear();
        target.chanceRemainders.putAll(source.chanceRemainders);
    }

    /**
     * Returns the completed expected quantity without float narrowing.
     * @param original total cost
     * @param remaining remaining cost, or null when complete
     * @return completed amount rounded upward, as in EMI's progress display
     */
    public static long completedAmount(FlatMaterialCost original, FlatMaterialCost remaining) {
        if (remaining == null) {
            return original.getEffectiveAmount();
        }
        return expected(original).subtract(expected(remaining)).max(Fraction.ZERO).roundLong(RoundingMode.CEILING);
    }

    private static Fraction expected(FlatMaterialCost cost) {
        if (cost instanceof ExactChanceMaterialCost exact) {
            return exact.expected;
        }
        return cost instanceof ChanceMaterialCost chance
                ? Fraction.of(cost.amount).multiply(Fraction.ofChance(chance.chance))
                : Fraction.of(cost.amount);
    }

    // EMI owns the cost model. Only the expected quantity needs a wider intermediate;
    // inherited amount/chance remain available to EMI renderers and tooltips.
    private static final class ExactChanceMaterialCost extends ChanceMaterialCost {
        private Fraction expected;

        private ExactChanceMaterialCost(EmiIngredient ingredient, long amount, Fraction chance) {
            super(ingredient, amount, chance.floatValue());
            expected = ForestAmounts.checkExpected(Fraction.of(amount).multiply(chance));
        }

        private void mergeExact(long amount, Fraction chance) {
            long sum = Math.addExact(this.amount, amount);
            Fraction next = ForestAmounts.checkExpected(expected.add(Fraction.of(amount).multiply(chance)));
            this.amount = sum;
            expected = next;
            this.chance = sum == 0 ? 0 : next.divide(Fraction.of(sum)).floatValue();
        }

        @Override
        public void merge(long amount, float chance) {
            mergeExact(amount, Fraction.ofChance(chance));
        }

        private void setExpected(Fraction value) {
            expected = ForestAmounts.checkExpected(value);
            amount = 1;
            chance = value.floatValue();
        }

        @Override
        public long getEffectiveAmount() {
            return Math.max(minBatch, ForestAmounts.roundExpected(expected));
        }
    }

    private static final class NodeProgress {
        private ProgressState progress = ProgressState.UNSTARTED;
        private long totalNeeded;
        private long neededBatches;
    }

    private static final class Accumulator {
        private final TreeCost result;
        private final Map<MaterialNode, NodeProgress> updates = new IdentityHashMap<>();
        private final Map<EmiRecipe, Long> recipeAmounts = new HashMap<>();

        private Accumulator(TreeCost result) {
            this.result = result;
        }

        private void calculate(MaterialNode node, long batches, boolean trackProgress) {
            calculateCost(node, Math.multiplyExact(batches, node.amount), Chance.DEFAULT, trackProgress);
        }

        private void addCost(EmiIngredient ingredient, long amount, long minBatch, Chance chance) {
            if (chance.chanced) {
                ChanceMaterialCost cost = result.chanceCosts.get(ingredient);
                if (cost == null) {
                    cost = new ExactChanceMaterialCost(ingredient, amount, chance.value);
                    result.chanceCosts.put(ingredient, cost);
                } else {
                    ((ExactChanceMaterialCost) cost).mergeExact(amount, chance.value);
                }
                cost.minBatch(minBatch);
            } else {
                FlatMaterialCost cost = result.costs.get(ingredient);
                if (cost == null) {
                    result.costs.put(ingredient, new FlatMaterialCost(ingredient, amount));
                } else {
                    cost.amount = Math.addExact(cost.amount, amount);
                }
            }
        }

        private void addRemainder(EmiStack stack, long amount, Chance chance) {
            if (amount <= 0) {
                return;
            }
            EmiStack key = stack.copy().setAmount(1);
            if (chance.chanced) {
                ChanceMaterialCost remainder = result.chanceRemainders.get(key);
                if (remainder == null) {
                    result.chanceRemainders.put(key, new ExactChanceMaterialCost(key, amount, chance.value));
                } else {
                    ((ExactChanceMaterialCost) remainder).mergeExact(amount, chance.value);
                }
            } else {
                FlatMaterialCost remainder = result.remainders.get(key);
                if (remainder == null) {
                    result.remainders.put(key, new FlatMaterialCost(key, amount));
                } else {
                    remainder.amount = Math.addExact(remainder.amount, amount);
                }
            }
        }

        private Fraction takeChancedRemainder(EmiStack stack, Fraction desired,
                boolean catalyst, Chance chance) {
            ExactChanceMaterialCost chanced = (ExactChanceMaterialCost) result.chanceRemainders.get(stack);
            if (chanced != null) {
                Fraction effective = chanced.expected;
                Fraction given = effective.min(desired);
                if (!catalyst) {
                    Fraction leftover = effective.compareTo(desired) >= 0
                            ? effective.subtract(desired)
                            : effective.subtract(given.multiply(chance.value));
                    // EMI's partial-consumption formula can produce a negative
                    // leftover for production factors above one. It is depleted,
                    // so do not retain an unusable negative remainder.
                    if (leftover.signum() <= 0) {
                        result.chanceRemainders.remove(stack);
                    } else {
                        chanced.setExpected(leftover);
                    }
                }
                return given;
            }
            FlatMaterialCost flat = result.remainders.get(stack);
            if (flat != null) {
                Fraction effective = Fraction.of(flat.amount);
                Fraction given = effective.min(desired);
                if (!catalyst) {
                    if (effective.compareTo(desired) >= 0) {
                        // Match EMI's integer inventory remainder conversion, without double rounding.
                        flat.amount = effective.subtract(desired).roundLong(RoundingMode.DOWN);
                        if (flat.amount == 0) {
                            result.remainders.remove(stack);
                        }
                    } else {
                        result.remainders.remove(stack);
                    }
                }
                return given;
            }
            return Fraction.ZERO;
        }

        private long takeRemainder(EmiStack stack, long desired, boolean catalyst) {
            FlatMaterialCost remainder = result.remainders.get(stack);
            if (remainder == null) {
                return 0;
            }
            if (remainder.amount >= desired) {
                if (!catalyst) {
                    remainder.amount -= desired;
                    if (remainder.amount == 0) {
                        result.remainders.remove(stack);
                    }
                }
                return desired;
            }
            if (!catalyst) {
                result.remainders.remove(stack);
            }
            return remainder.amount;
        }

        private void complete(MaterialNode node) {
            NodeProgress update = new NodeProgress();
            update.progress = ProgressState.COMPLETED;
            updates.put(node, update);
            if (node.children != null) {
                for (MaterialNode child : node.children) {
                    complete(child);
                }
            }
        }

        private void calculateCost(MaterialNode node, long amount, Chance chance, boolean trackProgress) {
            if (trackProgress) {
                updates.put(node, new NodeProgress());
            }
            boolean catalyst = EmiCompatibility.isCatalyst(node);
            if (catalyst) {
                amount = node.amount;
            }
            EmiRecipe recipe = node.recipe;
            if (recipe instanceof EmiResolutionRecipe resolution) {
                calculateCost(node.children.get(0), amount, chance, trackProgress);
                if (catalyst) {
                    addRemainder(resolution.stack, amount, chance);
                }
                return;
            }

            long original = amount;
            List<EmiStack> ingredientStacks = node.ingredient.getEmiStacks();
            for (EmiStack stack : ingredientStacks) {
                if (chance.chanced) {
                    Fraction desired = ForestAmounts.checkExpected(Fraction.of(amount).multiply(chance.value));
                    Fraction given = takeChancedRemainder(stack, desired, catalyst, chance);
                    if (given.signum() > 0) {
                        if (given.compareTo(desired) == 0) {
                            amount = 0;
                            continue;
                        }
                        long consumed = given.divide(chance.value).roundLong(RoundingMode.DOWN);
                        amount = Math.subtractExact(amount, consumed);
                        if (amount > 0) {
                            // Keep the unconsumed expectation exact while retaining EMI's integer batch count.
                            chance = new Chance(desired.subtract(given).divide(Fraction.of(amount)), true);
                        }
                    }
                } else {
                    amount -= takeRemainder(stack, amount, catalyst);
                }
            }
            if (amount == 0) {
                if (trackProgress) {
                    complete(node);
                }
                return;
            }
            if (trackProgress && amount != original) {
                updates.get(node).progress = ProgressState.PARTIAL;
            }

            if (recipe == null) {
                addCost(node.ingredient, amount, node.amount, chance);
                return;
            }

            long batches = ForestAmounts.ceilDiv(amount, node.divisor);
            long effectiveCrafts = Math.multiplyExact(batches, node.divisor);
            // EMI's favorite counter sums totalNeeded per recipe. Since divisor >= 1,
            // its neededBatches sum cannot exceed this checked quantity sum.
            recipeAmounts.merge(recipe, amount, Math::addExact);
            if (trackProgress) {
                updates.get(node).totalNeeded = amount;
                updates.get(node).neededBatches = batches;
            }
            Chance produced = chance.produce(node.produceChance);
            for (MaterialNode child : node.children) {
                calculateCost(child, Math.multiplyExact(batches, child.amount), produced.consume(child.consumeChance), trackProgress);
            }

            EmiStack output = node.ingredient.getEmiStacks().get(0);
            addRemainder(output, effectiveCrafts - amount, produced);
            for (EmiStack otherOutput : recipe.getOutputs()) {
                if (!output.equals(otherOutput)) {
                    addRemainder(otherOutput, Math.multiplyExact(batches, otherOutput.getAmount()), produced.consume(otherOutput.getChance()));
                }
            }
            for (MaterialNode child : node.children) {
                if (!child.remainder.isEmpty() && child.remainderAmount > 0) {
                    long remainderAmount = EmiCompatibility.isCatalyst(child)
                            ? child.remainderAmount
                            : Math.multiplyExact(batches, child.remainderAmount);
                    addRemainder(child.remainder, remainderAmount, produced.consume(child.consumeChance));
                }
            }
        }
    }
}
