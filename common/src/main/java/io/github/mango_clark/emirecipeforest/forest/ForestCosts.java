package io.github.mango_clark.emirecipeforest.forest;

import java.util.List;

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

/** Calculates forest-wide costs while carrying inventory and remainders between roots. */
public final class ForestCosts {
    private final TreeCost total = new TreeCost();
    private final TreeCost progress = new TreeCost();

    public TreeCost getTotal() {
        return total;
    }

    public TreeCost getProgress() {
        return progress;
    }

    public void calculate(List<MaterialTree> trees, EmiPlayerInventory inventory) {
        Accumulator progressAccumulator = new Accumulator(progress);
        progressAccumulator.clear();
        if (inventory != null) {
            for (EmiStack stack : inventory.inventory.values()) {
                EmiStack copy = stack.copy();
                progress.remainders.put(copy, new FlatMaterialCost(copy, copy.getAmount()));
            }
        }
        for (MaterialTree tree : trees) {
            if (tree != null && tree.goal != null) {
                progressAccumulator.calculate(tree.goal, tree.batches, true);
            }
        }

        Accumulator totalAccumulator = new Accumulator(total);
        totalAccumulator.clear();
        for (MaterialTree tree : trees) {
            if (tree != null && tree.goal != null) {
                totalAccumulator.calculate(tree.goal, tree.batches, false);
            }
        }
    }

    public static ForestCosts calculateNew(List<MaterialTree> trees, EmiPlayerInventory inventory) {
        ForestCosts costs = new ForestCosts();
        costs.calculate(trees, inventory);
        return costs;
    }

    private static final class Accumulator {
        private final TreeCost result;

        private Accumulator(TreeCost result) {
            this.result = result;
        }

        private void clear() {
            result.costs.clear();
            result.chanceCosts.clear();
            result.remainders.clear();
            result.chanceRemainders.clear();
        }

        private void calculate(MaterialNode node, long batches, boolean trackProgress) {
            calculateCost(node, batches * node.amount, Chance.DEFAULT, trackProgress);
        }

        private void addCost(EmiIngredient ingredient, long amount, long minBatch, Chance chance) {
            if (chance.chanced) {
                ChanceMaterialCost cost = result.chanceCosts.get(ingredient);
                if (cost == null) {
                    cost = new ChanceMaterialCost(ingredient, amount, chance.value);
                    result.chanceCosts.put(ingredient, cost);
                } else {
                    cost.merge(amount, chance.value);
                }
                cost.minBatch(minBatch);
            } else {
                FlatMaterialCost cost = result.costs.get(ingredient);
                if (cost == null) {
                    result.costs.put(ingredient, new FlatMaterialCost(ingredient, amount));
                } else {
                    cost.amount += amount;
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
                    result.chanceRemainders.put(key, new ChanceMaterialCost(key, amount, chance.value));
                } else {
                    remainder.merge(amount, chance.value);
                }
            } else {
                FlatMaterialCost remainder = result.remainders.get(key);
                if (remainder == null) {
                    result.remainders.put(key, new FlatMaterialCost(key, amount));
                } else {
                    remainder.amount += amount;
                }
            }
        }

        private double takeChancedRemainder(EmiStack stack, double desired, boolean catalyst, Chance chance) {
            double given = 0;
            ChanceMaterialCost chanced = result.chanceRemainders.get(stack);
            if (chanced != null) {
                double effective = chanced.amount * chanced.chance;
                if (effective >= desired) {
                    if (!catalyst) {
                        chanced.amount = 1;
                        chanced.chance = (float) (effective - desired);
                        if (chanced.chance == 0) {
                            result.chanceRemainders.remove(stack);
                        }
                    }
                    return desired;
                }
                given = effective;
                if (!catalyst) {
                    double leftover = effective - given * chance.value;
                    if (leftover == 0) {
                        result.chanceRemainders.remove(stack);
                    } else {
                        chanced.amount = 1;
                        chanced.chance = (float) leftover;
                    }
                }
            } else {
                FlatMaterialCost flat = result.remainders.get(stack);
                if (flat != null) {
                    if (flat.amount >= desired) {
                        if (!catalyst) {
                            flat.amount -= desired;
                            if (flat.amount == 0) {
                                result.remainders.remove(stack);
                            }
                        }
                        return desired;
                    }
                    if (!catalyst) {
                        result.remainders.remove(stack);
                    }
                    given += flat.amount;
                }
            }
            return given;
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
            node.progress = ProgressState.COMPLETED;
            node.totalNeeded = 0;
            node.neededBatches = 0;
            if (node.children != null) {
                for (MaterialNode child : node.children) {
                    complete(child);
                }
            }
        }

        private void calculateCost(MaterialNode node, long amount, Chance chance, boolean trackProgress) {
            if (trackProgress) {
                node.progress = ProgressState.UNSTARTED;
                node.totalNeeded = 0;
                node.neededBatches = 0;
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
                    double desired = amount * chance.value;
                    double given = takeChancedRemainder(stack, desired, catalyst, chance);
                    if (given > 0) {
                        double scaled = given / chance.value;
                        amount -= (long) scaled;
                        if (amount > 0) {
                            chance = new Chance((float) ((amount - scaled % 1) * chance.value / amount), true);
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
                node.progress = ProgressState.PARTIAL;
            }

            if (recipe == null) {
                addCost(node.ingredient, amount, node.amount, chance);
                return;
            }

            long batches = (long) Math.ceil(amount / (double) node.divisor);
            long effectiveCrafts = batches * node.divisor;
            if (trackProgress) {
                node.totalNeeded = amount;
                node.neededBatches = batches;
            }
            Chance produced = chance.produce(node.produceChance);
            for (MaterialNode child : node.children) {
                calculateCost(child, batches * child.amount, produced.consume(child.consumeChance), trackProgress);
            }

            EmiStack output = node.ingredient.getEmiStacks().get(0);
            addRemainder(output, effectiveCrafts - amount, produced);
            for (EmiStack otherOutput : recipe.getOutputs()) {
                if (!output.equals(otherOutput)) {
                    addRemainder(otherOutput, batches * otherOutput.getAmount(), produced.consume(otherOutput.getChance()));
                }
            }
            for (MaterialNode child : node.children) {
                if (!child.remainder.isEmpty() && child.remainderAmount > 0) {
                    long remainderAmount = EmiCompatibility.isCatalyst(child)
                            ? child.remainderAmount
                            : batches * child.remainderAmount;
                    addRemainder(child.remainder, remainderAmount, produced.consume(child.consumeChance));
                }
            }
        }
    }

    private record Chance(float value, boolean chanced) {
        private static final Chance DEFAULT = new Chance(1, false);

        private Chance produce(float chance) {
            return chance == 1 ? this : new Chance(value / chance, true);
        }

        private Chance consume(float chance) {
            return chance == 1 ? this : new Chance(value * chance, true);
        }
    }
}
