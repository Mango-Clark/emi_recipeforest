package io.github.mango_clark.emirecipeforest.forest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.FoldState;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import io.github.mango_clark.emirecipeforest.Constants;
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
    private static ReloadSnapshot reloadSnapshot;

    private ForestManager() {
    }

    /** Returns roots in their canonical order.
     * @return immutable root view
     */
    public static List<MaterialTree> getTrees() {
        return Collections.unmodifiableList(TREES);
    }

    /** Returns the live root count.
     * @return number of roots
     */
    public static int size() {
        return TREES.size();
    }

    /** Reports whether the forest is empty.
     * @return whether no roots exist
     */
    public static boolean isEmpty() {
        return TREES.isEmpty();
    }

    /** Returns the selected index.
     * @return root index, or {@code -1} when empty
     */
    public static int getSelectedIndex() {
        return selectedIndex;
    }

    /** Returns the selected live tree.
     * @return selected tree, or {@code null}
     */
    public static MaterialTree getSelectedTree() {
        if (selectedIndex < 0 || selectedIndex >= TREES.size()) {
            return null;
        }
        return TREES.get(selectedIndex);
    }

    /** Reports whether forest crafting favorites are enabled.
     * @return crafting-mode state
     */
    public static boolean isCraftingMode() {
        return craftingMode;
    }

    /**
     * Sets crafting mode and mirrors it into EMI; an empty forest always disables it.
     *
     * @param enabled requested mode
     */
    public static void setCraftingMode(boolean enabled) {
        craftingMode = enabled && !TREES.isEmpty();
        synchronizeSelectedTree();
    }

    /** Toggles crafting mode subject to the empty-forest invariant. */
    public static void toggleCraftingMode() {
        setCraftingMode(!craftingMode);
    }

    /**
     * Replaces the forest with EMI's canonical tree for the supplied goal.
     *
     * @param recipe recipe that becomes the only root
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

    /** Reports whether RecipeForest is invoking EMI's goal setter itself.
     * @return synchronization guard state
     */
    public static boolean isSynchronizingGoal() {
        return synchronizingGoal;
    }

    /**
     * Appends and selects a new root, or initializes EMI's canonical tree when empty.
     *
     * @param recipe supported root recipe
     * @return newly selected material tree
     */
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

    /**
     * Reports whether a live root uses the supplied recipe.
     *
     * @param recipe recipe to find
     * @return whether at least one root has the same recipe identity
     */
    public static boolean containsRecipe(EmiRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        return TREES.stream().anyMatch(tree -> tree != null && tree.goal != null
                && sameRecipe(tree.goal.recipe, recipe));
    }

    private static boolean sameRecipe(EmiRecipe left, EmiRecipe right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null || right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return left.equals(right);
    }

    /**
     * Selects a root and mirrors it into EMI.
     *
     * @param index root index
     * @return whether the index was valid
     */
    public static boolean select(int index) {
        if (index < 0 || index >= TREES.size()) {
            return false;
        }
        cancelPendingResolution();
        selectedIndex = index;
        synchronizeSelectedTree();
        return true;
    }

    /**
     * Removes a root while preserving a valid selection and EMI mirror state.
     *
     * @param index root index
     * @return removed tree, or {@code null} for an invalid index
     */
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

    /**
     * Moves a root while preserving the selected tree by identity.
     *
     * @param fromIndex current root index
     * @param toIndex destination root index
     * @return whether both indices were valid
     */
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

    /** Clears live state and any deferred reload snapshot. */
    public static synchronized void clear() {
        reloadSnapshot = null;
        clearLiveState();
    }

    /** Captures reload-safe values before EMI invalidates its live recipe objects. */
    public static synchronized void beginRecipeReload() {
        if (reloadSnapshot == null && !TREES.isEmpty()) {
            List<ReloadRoot> roots = new ArrayList<>();
            for (int i = 0; i < TREES.size(); i++) {
                ReloadRoot root = ReloadRoot.capture(TREES.get(i), i);
                if (root != null) {
                    roots.add(root);
                }
            }
            if (!roots.isEmpty()) {
                reloadSnapshot = new ReloadSnapshot(List.copyOf(roots), selectedIndex, craftingMode);
            }
        }
        clearLiveState();
    }

    /** Rebuilds the live forest exclusively from EMI's newly loaded recipe objects. */
    public static synchronized void finishRecipeReload() {
        ReloadSnapshot snapshot = reloadSnapshot;
        if (snapshot == null) {
            return;
        }
        reloadSnapshot = null;
        clearLiveState();

        int restoredSelected = -1;
        for (ReloadRoot root : snapshot.roots) {
            EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(root.recipeId));
            if (recipe == null || !recipe.supportsRecipeTree()) {
                continue;
            }
            try {
                MaterialTree tree = new MaterialTree(recipe);
                tree.batches = root.batches;
                root.restoreResolutions(tree);
                root.restoreFolds(tree.goal);
                if (root.originalIndex == snapshot.selectedIndex) {
                    restoredSelected = TREES.size();
                }
                TREES.add(tree);
            } catch (RuntimeException exception) {
                Constants.LOG.warn("Skipping RecipeForest root '{}' after EMI reload", root.recipeId, exception);
            }
        }

        if (!TREES.isEmpty()) {
            selectedIndex = restoredSelected >= 0 ? restoredSelected
                    : Math.min(snapshot.selectedIndex, TREES.size() - 1);
            craftingMode = snapshot.craftingMode;
        }
        synchronizeSelectedTree();
    }

    private static void clearLiveState() {
        cancelPendingResolution();
        TREES.clear();
        selectedIndex = -1;
        craftingMode = false;
        synchronizeSelectedTree();
    }

    /**
     * Applies a resolution using the persisted default scope.
     *
     * @param ingredient ingredient being resolved
     * @param recipe selected recipe, or {@code null} to clear the resolution
     */
    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe) {
        addResolution(ingredient, recipe, ForestBookmarks.getResolutionScope());
    }

    /**
     * Compatibility overload selecting all roots or only the selected root.
     *
     * @param ingredient ingredient being resolved
     * @param recipe selected recipe, or {@code null}
     * @param allTrees whether to apply to every root
     */
    public static void addResolution(EmiIngredient ingredient, EmiRecipe recipe, boolean allTrees) {
        addResolution(ingredient, recipe, allTrees ? ResolutionScope.ALL_ROOTS : ResolutionScope.SELECTED_ROOT);
    }

    /**
     * Applies a recipe resolution to roots selected by {@code scope}.
     *
     * @param ingredient ingredient being resolved
     * @param recipe selected recipe, or {@code null}
     * @param scope roots that receive the resolution
     */
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

    /**
     * Captures the selected tree and scope before opening EMI's resolution picker.
     *
     * @param ingredient ingredient expected from the picker
     * @param scope roots that should receive a successful selection
     */
    public static void beginPendingResolution(EmiIngredient ingredient, ResolutionScope scope) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(scope, "scope");
        MaterialTree selected = getSelectedTree();
        pendingResolution = selected == null ? null : new PendingResolution(ingredient, selected, scope);
    }

    /**
     * Consumes a pending picker context exactly once.
     * A mismatched source or ingredient clears stale context without applying it.
     *
     * @param source tree that received EMI's resolution
     * @param ingredient resolved ingredient
     * @param recipe selected recipe, or {@code null}
     * @return whether the pending context matched and was applied
     */
    public static boolean tryApplyPendingResolution(MaterialTree source, EmiIngredient ingredient, EmiRecipe recipe) {
        PendingResolution pending = pendingResolution;
        if (pending == null || applyingPendingResolution) {
            return false;
        }
        if (source == null || source != getSelectedTree() || source != pending.source
                || !Objects.equals(pending.ingredient, ingredient)) {
            pendingResolution = null;
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

    /** Cancels any pending picker context. */
    public static void cancelPendingResolution() {
        pendingResolution = null;
    }

    /** Reports whether a picker result is expected.
     * @return pending-resolution state
     */
    public static boolean hasPendingResolution() {
        return pendingResolution != null;
    }

    /** Recalculates every live root and restores EMI's selected-tree mirror. */
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

    private record ReloadSnapshot(List<ReloadRoot> roots, int selectedIndex, boolean craftingMode) {
    }

    private record ReloadRoot(String recipeId, long batches, int originalIndex,
            List<ReloadResolution> resolutions, Map<String, FoldState> folds) {
        private static ReloadRoot capture(MaterialTree tree, int originalIndex) {
            if (tree == null || tree.goal == null || tree.goal.recipe == null || tree.goal.recipe.getId() == null) {
                return null;
            }
            List<ReloadResolution> resolutions = new ArrayList<>();
            for (Map.Entry<EmiIngredient, EmiRecipe> entry : tree.resolutions.entrySet()) {
                ReloadResolution resolution = ReloadResolution.capture(entry.getKey(), entry.getValue());
                if (resolution != null) {
                    resolutions.add(resolution);
                }
            }
            Map<String, FoldState> folds = new LinkedHashMap<>();
            captureFolds(tree.goal, "", folds);
            return new ReloadRoot(tree.goal.recipe.getId().toString(), Math.max(1, tree.batches), originalIndex,
                    List.copyOf(resolutions), Map.copyOf(folds));
        }

        private void restoreResolutions(MaterialTree tree) {
            for (ReloadResolution resolution : resolutions) {
                try {
                    resolution.restore(tree);
                } catch (RuntimeException exception) {
                    Constants.LOG.warn("Skipping RecipeForest resolution in root '{}' after EMI reload",
                            recipeId, exception);
                }
            }
        }

        private void restoreFolds(MaterialNode root) {
            folds.forEach((path, state) -> {
                MaterialNode node = findNode(root, path);
                if (node != null) {
                    node.state = state;
                }
            });
        }

        private static void captureFolds(MaterialNode node, String path, Map<String, FoldState> folds) {
            folds.put(path, node.state);
            if (node.children != null) {
                for (int i = 0; i < node.children.size(); i++) {
                    captureFolds(node.children.get(i), path.isEmpty() ? Integer.toString(i) : path + '/' + i, folds);
                }
            }
        }

        private static MaterialNode findNode(MaterialNode node, String path) {
            if (path.isEmpty()) {
                return node;
            }
            for (String part : path.split("/")) {
                if (node.children == null) {
                    return null;
                }
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= node.children.size()) {
                        return null;
                    }
                    node = node.children.get(index);
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
            return node;
        }
    }

    private record ReloadResolution(JsonElement ingredient, String recipeId, JsonElement selectedStack) {
        private static ReloadResolution capture(EmiIngredient ingredient, EmiRecipe recipe) {
            JsonElement ingredientJson = EmiIngredientSerializer.getSerialized(ingredient);
            if (ingredientJson == null) {
                return null;
            }
            if (recipe == null) {
                return new ReloadResolution(ingredientJson.deepCopy(), null, null);
            }
            if (recipe instanceof EmiResolutionRecipe resolution) {
                JsonElement stackJson = EmiIngredientSerializer.getSerialized(resolution.stack);
                return stackJson == null ? null
                        : new ReloadResolution(ingredientJson.deepCopy(), null, stackJson.deepCopy());
            }
            return recipe.getId() == null ? null
                    : new ReloadResolution(ingredientJson.deepCopy(), recipe.getId().toString(), null);
        }

        private void restore(MaterialTree tree) {
            EmiIngredient key = EmiIngredientSerializer.getDeserialized(ingredient.deepCopy());
            if (key.isEmpty()) {
                return;
            }
            EmiRecipe recipe = null;
            if (selectedStack != null) {
                EmiIngredient stack = EmiIngredientSerializer.getDeserialized(selectedStack.deepCopy());
                if (stack.isEmpty() || stack.getEmiStacks().size() != 1) {
                    return;
                }
                recipe = new EmiResolutionRecipe(key, stack.getEmiStacks().get(0));
            } else if (recipeId != null) {
                recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(recipeId));
                if (recipe == null) {
                    return;
                }
            }
            tree.addResolution(key, recipe);
        }
    }
}
