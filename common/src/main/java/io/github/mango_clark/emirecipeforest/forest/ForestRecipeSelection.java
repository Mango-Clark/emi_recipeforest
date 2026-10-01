package io.github.mango_clark.emirecipeforest.forest;

import java.util.List;
import java.util.function.IntUnaryOperator;

import dev.emi.emi.api.recipe.EmiRecipe;

/** Selects from EMI's current recipe list without copying or caching it. */
public final class ForestRecipeSelection {
    private ForestRecipeSelection() {
    }

    /**
     * Reservoir sampling: every supported recipe has equal probability in one pass.
     * @param recipes current EMI recipes
     * @param nextInt native random source with an exclusive positive bound
     * @return a supported recipe, or null if none exists
     */
    public static EmiRecipe choose(List<EmiRecipe> recipes, IntUnaryOperator nextInt) {
        EmiRecipe selected = null;
        int supported = 0;
        for (EmiRecipe recipe : recipes) {
            if (recipe.supportsRecipeTree() && (++supported == 1 || nextInt.applyAsInt(supported) == 0)) {
                selected = recipe;
            }
        }
        return selected;
    }
}
