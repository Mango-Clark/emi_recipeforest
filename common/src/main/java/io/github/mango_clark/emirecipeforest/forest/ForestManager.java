package io.github.mango_clark.emirecipeforest.forest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialTree;

/**
 * Owns the live, session-only collection of recipe trees.
 *
 * <p>The selected tree and crafting mode are mirrored into EMI's singleton
 * {@link BoM} state so EMI's existing recipe resolution and tree interactions
 * continue to operate on the active forest root.</p>
 */
public final class ForestManager {
    private static final List<MaterialTree> TREES = new ArrayList<>();

    private static int selectedIndex = -1;
    private static boolean craftingMode;
    private static boolean synchronizingGoal;

    private ForestManager() {
    }

    public static List<MaterialTree> getTrees() {
        return Collections.unmodifiableList(TREES);
    }

    public static int size() {
        return TREES.size();
    }

    public static boolean isEmpty() {
        return TREES.isEmpty();
    }

    public static int getSelectedIndex() {
        return selectedIndex;
    }

    public static MaterialTree getSelectedTree() {
        if (selectedIndex < 0 || selectedIndex >= TREES.size()) {
            return null;
        }
        return TREES.get(selectedIndex);
    }

    public static boolean isCraftingMode() {
        return craftingMode;
    }

    public static void setCraftingMode(boolean enabled) {
        craftingMode = enabled && !TREES.isEmpty();
        synchronizeSelectedTree();
    }

    public static void toggleCraftingMode() {
        setCraftingMode(!craftingMode);
    }

    /**
     * Replaces the forest with EMI's canonical tree for the supplied goal.
     */
    public static void replaceSolo(EmiRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        synchronizingGoal = true;
        try {
            BoM.setGoal(recipe);
        } finally {
            synchronizingGoal = false;
        }

        TREES.clear();
        if (BoM.tree != null) {
            TREES.add(BoM.tree);
            selectedIndex = 0;
        } else {
            selectedIndex = -1;
        }
        craftingMode = false;
        synchronizeSelectedTree();
    }

    /**
     * Synchronizes a goal set by EMI outside RecipeForest.
     *
     * <p>Call this from a tail injection into {@link BoM#setGoal(EmiRecipe)}.</p>
     */
    public static void synchronizeSetGoal() {
        if (synchronizingGoal) {
            return;
        }
        TREES.clear();
        if (BoM.tree != null) {
            TREES.add(BoM.tree);
            selectedIndex = 0;
        } else {
            selectedIndex = -1;
        }
        craftingMode = BoM.craftingMode && !TREES.isEmpty();
        synchronizeSelectedTree();
    }

    public static boolean isSynchronizingGoal() {
        return synchronizingGoal;
    }

    public static MaterialTree add(EmiRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        if (TREES.isEmpty()) {
            replaceSolo(recipe);
            return getSelectedTree();
        }

        MaterialTree tree = new MaterialTree(recipe);
        TREES.add(tree);
        selectedIndex = TREES.size() - 1;
        synchronizeSelectedTree();
        return tree;
    }

    public static boolean select(int index) {
        if (index < 0 || index >= TREES.size()) {
            return false;
        }
        selectedIndex = index;
        synchronizeSelectedTree();
        return true;
    }

    public static MaterialTree remove(int index) {
        if (index < 0 || index >= TREES.size()) {
            return null;
        }

        MaterialTree removed = TREES.remove(index);
        if (TREES.isEmpty()) {
            selectedIndex = -1;
            craftingMode = false;
        } else {
            selectedIndex = Math.min(index, TREES.size() - 1);
        }
        synchronizeSelectedTree();
        return removed;
    }

    public static void clear() {
        TREES.clear();
        selectedIndex = -1;
        craftingMode = false;
        synchronizeSelectedTree();
    }

    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe) {
        addResolution(ingredient, recipe, false);
    }

    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe, boolean allTrees) {
        Objects.requireNonNull(ingredient, "ingredient");
        if (allTrees) {
            for (MaterialTree tree : TREES) {
                tree.addResolution(ingredient, recipe);
            }
        } else {
            MaterialTree tree = getSelectedTree();
            if (tree != null) {
                tree.addResolution(ingredient, recipe);
            }
        }
        synchronizeSelectedTree();
    }

    public static void recalculateAll() {
        for (MaterialTree tree : TREES) {
            tree.recalculate();
        }
        synchronizeSelectedTree();
    }

    private static void synchronizeSelectedTree() {
        BoM.tree = getSelectedTree();
        BoM.craftingMode = craftingMode;
    }
}
