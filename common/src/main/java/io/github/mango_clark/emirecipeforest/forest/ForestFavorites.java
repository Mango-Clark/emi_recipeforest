package io.github.mango_clark.emirecipeforest.forest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.ChanceMaterialCost;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.bom.TreeCost;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Builds EMI crafting favorites for every live forest root. */
public final class ForestFavorites {
    private static final RecipeSyntheticFactory RECIPE_SYNTHETIC_FACTORY = resolveRecipeSyntheticFactory();

    private ForestFavorites() {
    }

    /**
     * Rebuilds EMI's synthetic crafting favorites from the live forest.
     * Clears the synthetic list and disables crafting mode when no actionable recipe remains.
     *
     * @param inventory current EMI player inventory
     */
    public static void updateSynthetic(EmiPlayerInventory inventory) {
        List<MaterialTree> trees = ForestManager.getTrees();
        EmiFavorites.syntheticFavorites.clear();
        if (trees.isEmpty() || !ForestManager.isCraftingMode()) {
            return;
        }

        EmiPlayerInventory emptyInventory = new EmiPlayerInventory(List.of());
        emptyInventory.inventory.clear();
        ForestCosts original = ForestCosts.calculateNew(trees, emptyInventory);
        Object2LongMap<EmiRecipe> originalBatches = new Object2LongLinkedOpenHashMap<>();
        Object2LongMap<EmiRecipe> originalAmounts = new Object2LongLinkedOpenHashMap<>();
        countRecipes(trees, originalBatches, originalAmounts);

        ForestCosts remaining = ForestCosts.calculateNew(trees, inventory);
        Object2LongMap<EmiRecipe> batches = new Object2LongLinkedOpenHashMap<>();
        Object2LongMap<EmiRecipe> amounts = new Object2LongLinkedOpenHashMap<>();
        countRecipes(trees, batches, amounts);

        boolean hasSomething = false;
        for (Object2LongMap.Entry<EmiRecipe> entry : batches.object2LongEntrySet()) {
            EmiRecipe recipe = entry.getKey();
            long amount = amounts.getOrDefault(recipe, 0);
            long batch = entry.getLongValue();
            if (amount == 0) {
                continue;
            }
            hasSomething = true;
            int state = inventory.canCraft(recipe, batch) ? 2 : inventory.canCraft(recipe) ? 1 : 0;
            long total = originalAmounts.getOrDefault(recipe, amount);
            EmiFavorites.syntheticFavorites.add(RECIPE_SYNTHETIC_FACTORY.create(recipe, batch, amount, total, state));
        }

        if (!hasSomething) {
            ForestManager.setCraftingMode(false);
            return;
        }

        TreeCost originalCost = original.getTotal();
        TreeCost remainingCost = remaining.getProgress();
        Map<EmiIngredient, FlatMaterialCost> originalCosts = Maps.newHashMap(originalCost.costs);
        Map<EmiIngredient, ChanceMaterialCost> originalChanceCosts = Maps.newHashMap(originalCost.chanceCosts);
        for (FlatMaterialCost cost : remainingCost.costs.values()) {
            if (cost.amount > 0) {
                long total = originalCosts.getOrDefault(cost.ingredient, cost).amount;
                EmiFavorites.syntheticFavorites.add(new EmiFavorite.Synthetic(cost.ingredient, cost.amount, total));
            }
        }
        for (ChanceMaterialCost cost : remainingCost.chanceCosts.values()) {
            if (cost.getEffectiveAmount() <= 0) {
                continue;
            }
            long needed = cost.getEffectiveAmount();
            if (originalChanceCosts.containsKey(cost.ingredient)) {
                ChanceMaterialCost originalChance = originalChanceCosts.get(cost.ingredient);
                long done = (long) Math.ceil(originalChance.amount * originalChance.chance
                        - cost.amount * cost.chance);
                needed = originalChance.getEffectiveAmount() - done;
            }
            if (needed > 0) {
                EmiFavorites.syntheticFavorites.add(new EmiFavorite.Synthetic(cost.ingredient, needed, needed));
            }
        }
    }

    private static void countRecipes(List<MaterialTree> trees, Object2LongMap<EmiRecipe> batches,
            Object2LongMap<EmiRecipe> amounts) {
        for (MaterialTree tree : trees) {
            if (tree != null && tree.goal != null) {
                EmiFavorites.countRecipes(batches, amounts, tree.goal);
            }
        }
    }

    private static RecipeSyntheticFactory resolveRecipeSyntheticFactory() {
        Class<EmiFavorite.Synthetic> type = EmiFavorite.Synthetic.class;
        try {
            Constructor<EmiFavorite.Synthetic> constructor = type.getConstructor(
                    EmiRecipe.class, long.class, long.class, long.class, int.class);
            return (recipe, batches, amount, total, state) -> instantiate(constructor,
                    recipe, batches, amount, total, state);
        } catch (NoSuchMethodException modernMissing) {
            try {
                Constructor<EmiFavorite.Synthetic> constructor = type.getConstructor(
                        EmiRecipe.class, long.class, long.class, int.class);
                return (recipe, batches, amount, total, state) -> instantiate(constructor,
                        recipe, batches, amount, state);
            } catch (NoSuchMethodException legacyMissing) {
                throw new IllegalStateException("Incompatible EMI Synthetic favorite recipe constructor; expected "
                        + "(EmiRecipe,long,long,long,int) or (EmiRecipe,long,long,int)", legacyMissing);
            }
        }
    }

    private static EmiFavorite.Synthetic instantiate(Constructor<EmiFavorite.Synthetic> constructor,
            Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to create an EMI Synthetic favorite", exception);
        }
    }

    @FunctionalInterface
    private interface RecipeSyntheticFactory {
        EmiFavorite.Synthetic create(EmiRecipe recipe, long batches, long amount, long total, int state);
    }
}
