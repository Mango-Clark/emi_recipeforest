package io.github.mango_clark.emirecipeforest.forest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;

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
    private static PendingResolution pendingResolution;
    private static boolean applyingPendingResolution;

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
        cancelPendingResolution();
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
        cancelPendingResolution();
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
        cancelPendingResolution();
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
        cancelPendingResolution();
        selectedIndex = index;
        synchronizeSelectedTree();
        return true;
    }

    public static MaterialTree remove(int index) {
        if (index < 0 || index >= TREES.size()) {
            return null;
        }

        cancelPendingResolution();
        MaterialTree selected = getSelectedTree();
        boolean removingSelected = TREES.get(index) == selected;
        MaterialTree removed = TREES.remove(index);
        if (TREES.isEmpty()) {
            selectedIndex = -1;
            craftingMode = false;
        } else if (removingSelected) {
            selectedIndex = Math.min(index, TREES.size() - 1);
        } else {
            selectedIndex = identityIndexOf(selected);
        }
        synchronizeSelectedTree();
        return removed;
    }

    /** Moves a root while preserving the selected tree by identity. */
    public static boolean move(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= TREES.size() || toIndex < 0 || toIndex >= TREES.size()) {
            return false;
        }
        if (fromIndex == toIndex) {
            return true;
        }

        cancelPendingResolution();
        MaterialTree selected = getSelectedTree();
        MaterialTree moved = TREES.remove(fromIndex);
        TREES.add(toIndex, moved);
        selectedIndex = identityIndexOf(selected);
        synchronizeSelectedTree();
        return true;
    }

    public static void clear() {
        cancelPendingResolution();
        TREES.clear();
        selectedIndex = -1;
        craftingMode = false;
        synchronizeSelectedTree();
    }

    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe) {
        addResolution(ingredient, recipe, ForestBookmarks.getResolutionScope());
    }

    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe, boolean allTrees) {
        addResolution(ingredient, recipe, allTrees ? ResolutionScope.ALL_ROOTS : ResolutionScope.SELECTED_ROOT);
    }

    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe, ResolutionScope scope) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(scope, "scope");
        for (MaterialTree tree : TREES) {
            if (scope == ResolutionScope.ALL_ROOTS
                    || scope == ResolutionScope.SELECTED_ROOT && tree == getSelectedTree()
                    || scope == ResolutionScope.MATCHING_ROOTS && containsIngredient(tree.goal, ingredient)) {
                tree.addResolution(ingredient, recipe);
            }
        }
        synchronizeSelectedTree();
    }

    public static void beginPendingResolution(EmiIngredient ingredient, ResolutionScope scope) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(scope, "scope");
        MaterialTree selected = getSelectedTree();
        pendingResolution = selected == null ? null : new PendingResolution(ingredient, selected, scope);
    }

    public static boolean tryApplyPendingResolution(MaterialTree source, EmiIngredient ingredient, EmiRecipe recipe) {
        PendingResolution pending = pendingResolution;
        if (pending == null || applyingPendingResolution || source == null || source != getSelectedTree()
                || source != pending.source || !Objects.equals(pending.ingredient, ingredient)) {
            return false;
        }

        pendingResolution = null;
        applyingPendingResolution = true;
        try {
            addResolution(ingredient, recipe, pending.scope);
        } finally {
            applyingPendingResolution = false;
        }
        return true;
    }

    public static void cancelPendingResolution() {
        pendingResolution = null;
    }

    public static boolean hasPendingResolution() {
        return pendingResolution != null;
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

    private static boolean containsIngredient(MaterialNode node, EmiIngredient ingredient) {
        if (node == null) {
            return false;
        }
        if (Objects.equals(node.ingredient, ingredient)) {
            return true;
        }
        if (node.children != null) {
            for (MaterialNode child : node.children) {
                if (containsIngredient(child, ingredient)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int identityIndexOf(MaterialTree tree) {
        for (int i = 0; i < TREES.size(); i++) {
            if (TREES.get(i) == tree) {
                return i;
            }
        }
        return -1;
    }

    private record PendingResolution(EmiIngredient ingredient, MaterialTree source, ResolutionScope scope) {
    }
}
