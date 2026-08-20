package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.mango_clark.emirecipeforest.forest.QuantityDisplay;
import io.github.mango_clark.emirecipeforest.forest.QuantityDisplay.DisplayMode;

class ForestBehaviorTest {
    private static IsolatedEmiRuntime runtime;
    private static Class<?> manager;

    @TempDir
    Path gameDirectory;

    @BeforeAll
    static void createRuntime() throws Exception {
        runtime = new IsolatedEmiRuntime();
        manager = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestManager");
    }

    @AfterAll
    static void closeRuntime() throws Exception {
        runtime.close();
    }

    @BeforeEach
    void resetForest() throws Exception {
        Object minecraft = call(runtime.type("net.minecraft.client.Minecraft"), "getInstance", types());
        field(minecraft, "gameDirectory", gameDirectory.toFile());
        call(manager, "clear", types());
        call(runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks"), "load", types());
    }

    @Test
    void forestAllowsDuplicateRootsAndKeepsSelectionInsideRemovalBoundaries() throws Exception {
        Object output = runtime.stack("plank", 1);
        Object recipe = runtime.recipe("test:plank", output, List.of());

        assertTrue((boolean) call(manager, "isEmpty", types()));
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe);
        Object duplicate = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe);
        assertEquals(2, call(manager, "size", types()));
        assertNotSame(first, duplicate);
        assertEquals(1, call(manager, "getSelectedIndex", types()));

        assertFalse((boolean) call(manager, "select", types(int.class), -1));
        assertFalse((boolean) call(manager, "select", types(int.class), 2));
        assertTrue((boolean) call(manager, "select", types(int.class), 0));
        assertNull(call(manager, "remove", types(int.class), 2));
        assertSame(first, call(manager, "remove", types(int.class), 0));
        assertEquals(0, call(manager, "getSelectedIndex", types()));
        assertSame(duplicate, call(manager, "remove", types(int.class), 0));
        assertTrue((boolean) call(manager, "isEmpty", types()));
        assertEquals(-1, call(manager, "getSelectedIndex", types()));
    }

    @Test
    void listLengthDoesNotLimitRootsAndPersists() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        call(bookmarks, "setListLength", types(int.class), 2);

        Object recipe = runtime.recipe("test:limited", runtime.stack("limited", 1), List.of());
        assertNotSame(null, call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe));
        assertNotSame(null, call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe));
        assertNotSame(null, call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe));
        assertEquals(3, call(manager, "size", types()));
        assertTrue((boolean) call(manager, "containsRecipe", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe));
        assertTrue((boolean) call(manager, "containsRecipe", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:limited", runtime.stack("replacement", 1), List.of())));
        assertFalse((boolean) call(manager, "containsRecipe", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:other", runtime.stack("other", 1), List.of())));

        call(bookmarks, "setListLength", types(int.class), 1);
        assertEquals(1, call(bookmarks, "getListLength", types()));

        call(bookmarks, "load", types());
        assertEquals(1, call(bookmarks, "getListLength", types()));

        Object legacyRoot = jsonObject();
        addProperty(legacyRoot, "schema", 2);
        Object legacySettings = jsonObject();
        addProperty(legacySettings, "maxRoots", 4);
        jsonAdd(legacyRoot, "settings", legacySettings);
        jsonAdd(legacyRoot, "searches", jsonArray());
        jsonAdd(legacyRoot, "trees", jsonArray());
        Path config = gameDirectory.resolve("config/emi_recipeforest.json");
        Files.writeString(config, gsonString(legacyRoot));
        call(bookmarks, "load", types());
        assertEquals(4, call(bookmarks, "getListLength", types()));

        call(bookmarks, "setListLength", types(int.class), 3);
        Field gsonValues = runtime.type("com.google.gson.Gson").getDeclaredField("VALUES");
        gsonValues.setAccessible(true);
        Object savedRoot = ((Map<?, ?>) gsonValues.get(null)).get(Files.readString(config));
        Object savedSettings = invoke(savedRoot, "getAsJsonObject", types(String.class), "settings");
        assertTrue((boolean) invoke(savedSettings, "has", types(String.class), "listLength"));
        assertFalse((boolean) invoke(savedSettings, "has", types(String.class), "maxRoots"));
    }

    @Test
    void configSnapshotCountsAndRevertsRecipeForestChanges() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        Object original = call(bookmarks, "captureConfigState", types());

        call(bookmarks, "setBoxEnabled", types(boolean.class), false);
        call(bookmarks, "setRootGridSize", types(int.class, int.class), 3, 4);
        call(bookmarks, "setListLength", types(int.class), 12);
        Object changed = call(bookmarks, "captureConfigState", types());
        assertEquals(3, invoke(original, "countChanges", types(changed.getClass()), changed));

        call(bookmarks, "restoreConfigState", types(original.getClass()), original);
        assertEquals(0, invoke(original, "countChanges", types(original.getClass()),
                call(bookmarks, "captureConfigState", types())));
        assertTrue((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(8, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(2, call(bookmarks, "getRootGridRows", types()));
        assertEquals(64, call(bookmarks, "getListLength", types()));

        call(bookmarks, "load", types());
        assertTrue((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(8, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(2, call(bookmarks, "getRootGridRows", types()));
        assertEquals(64, call(bookmarks, "getListLength", types()));
    }

    @Test
    void movingRootsPreservesOrderAndSelectedTreeIdentity() throws Exception {
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:first", runtime.stack("first", 1), List.of()));
        Object second = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:second", runtime.stack("second", 1), List.of()));
        Object third = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:third", runtime.stack("third", 1), List.of()));
        call(manager, "select", types(int.class), 1);

        assertFalse((boolean) call(manager, "move", types(int.class, int.class), -1, 0));
        assertFalse((boolean) call(manager, "move", types(int.class, int.class), 0, 3));
        assertTrue((boolean) call(manager, "move", types(int.class, int.class), 0, 2));
        assertEquals(List.of(second, third, first), call(manager, "getTrees", types()));
        assertSame(second, call(manager, "getSelectedTree", types()));
        assertEquals(0, call(manager, "getSelectedIndex", types()));

        assertTrue((boolean) call(manager, "move", types(int.class, int.class), 0, 2));
        assertEquals(List.of(third, first, second), call(manager, "getTrees", types()));
        assertSame(second, call(manager, "getSelectedTree", types()));
        assertEquals(2, call(manager, "getSelectedIndex", types()));
        assertTrue((boolean) call(manager, "move", types(int.class, int.class), 2, 2));
    }

    @Test
    void removingRootsPreservesSelectionIdentityAndChoosesNearestNeighbor() throws Exception {
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:first", runtime.stack("first", 1), List.of()));
        Object second = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:second", runtime.stack("second", 1), List.of()));
        Object third = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:third", runtime.stack("third", 1), List.of()));
        Object fourth = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:fourth", runtime.stack("fourth", 1), List.of()));
        Object fifth = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:fifth", runtime.stack("fifth", 1), List.of()));
        call(manager, "select", types(int.class), 2);

        assertSame(first, call(manager, "remove", types(int.class), 0));
        assertSame(third, call(manager, "getSelectedTree", types()));
        assertEquals(1, call(manager, "getSelectedIndex", types()));

        assertSame(fifth, call(manager, "remove", types(int.class), 3));
        assertSame(third, call(manager, "getSelectedTree", types()));
        assertEquals(1, call(manager, "getSelectedIndex", types()));

        assertSame(third, call(manager, "remove", types(int.class), 1));
        assertSame(fourth, call(manager, "getSelectedTree", types()));
        assertEquals(1, call(manager, "getSelectedIndex", types()));

        assertSame(fourth, call(manager, "remove", types(int.class), 1));
        assertSame(second, call(manager, "getSelectedTree", types()));
        assertEquals(0, call(manager, "getSelectedIndex", types()));

        assertSame(second, call(manager, "remove", types(int.class), 0));
        assertTrue((boolean) call(manager, "isEmpty", types()));
        assertNull(call(manager, "getSelectedTree", types()));
        assertEquals(-1, call(manager, "getSelectedIndex", types()));
    }

    @Test
    void soloReplacementClearsForestAndCraftingMode() throws Exception {
        Object firstRecipe = runtime.recipe("test:first", runtime.stack("first", 1), List.of());
        Object soloRecipe = runtime.recipe("test:solo", runtime.stack("solo", 1), List.of());
        call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), firstRecipe);
        call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), firstRecipe);
        call(manager, "setCraftingMode", types(boolean.class), true);

        call(manager, "replaceSolo", types("dev.emi.emi.api.recipe.EmiRecipe"), soloRecipe);

        assertEquals(1, call(manager, "size", types()));
        assertEquals(0, call(manager, "getSelectedIndex", types()));
        assertFalse((boolean) call(manager, "isCraftingMode", types()));
        Object selected = call(manager, "getSelectedTree", types());
        assertSame(soloRecipe, field(field(selected, "goal"), "recipe"));
    }

    @Test
    void resolutionScopeDistinguishesSelectedTreeFromWholeForest() throws Exception {
        Class<?> scope = runtime.type(
                "io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$ResolutionScope");
        Object resolution = runtime.recipe("test:resolution", runtime.stack("resolved", 1), List.of());
        Object ingredient = runtime.stack("ore", 1);
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:first", runtime.stack("first", 1), List.of(ingredient)));
        Object middle = runtime.stack("middle", 1);
        Object second = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:second", runtime.stack("second", 1), List.of(middle)));
        Object secondChild = ((List<?>) field(field(second, "goal"), "children")).get(0);
        Object nestedMatch = runtime.type("dev.emi.emi.bom.MaterialNode")
                .getConstructor(runtime.type("dev.emi.emi.api.stack.EmiIngredient")).newInstance(ingredient);
        @SuppressWarnings("unchecked")
        List<Object> nestedChildren = (List<Object>) field(secondChild, "children");
        nestedChildren.add(nestedMatch);
        Object third = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:third", runtime.stack("third", 1), List.of(runtime.stack("other", 1))));
        call(manager, "select", types(int.class), 0);

        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", scope), ingredient, resolution,
                enumConstant(scope, "SELECTED_ROOT"));
        assertSame(resolution, castMap(field(first, "resolutions")).get(ingredient));
        assertTrue(castMap(field(second, "resolutions")).isEmpty());
        assertTrue(castMap(field(third, "resolutions")).isEmpty());
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", scope), ingredient, null,
                enumConstant(scope, "SELECTED_ROOT"));
        assertTrue(castMap(field(first, "resolutions")).containsKey(ingredient));
        assertNull(castMap(field(first, "resolutions")).get(ingredient));

        clearResolutions(first, second, third);
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", scope), ingredient, resolution,
                enumConstant(scope, "MATCHING_ROOTS"));
        assertSame(resolution, castMap(field(first, "resolutions")).get(ingredient));
        assertSame(resolution, castMap(field(second, "resolutions")).get(ingredient));
        assertTrue(castMap(field(third, "resolutions")).isEmpty());
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", scope), ingredient, null,
                enumConstant(scope, "MATCHING_ROOTS"));
        assertNull(castMap(field(first, "resolutions")).get(ingredient));
        assertNull(castMap(field(second, "resolutions")).get(ingredient));
        assertFalse(castMap(field(third, "resolutions")).containsKey(ingredient));

        clearResolutions(first, second, third);
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", boolean.class), ingredient, resolution, true);
        assertSame(resolution, castMap(field(first, "resolutions")).get(ingredient));
        assertSame(resolution, castMap(field(second, "resolutions")).get(ingredient));
        assertSame(resolution, castMap(field(third, "resolutions")).get(ingredient));
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", scope), ingredient, null,
                enumConstant(scope, "ALL_ROOTS"));
        assertNull(castMap(field(first, "resolutions")).get(ingredient));
        assertNull(castMap(field(second, "resolutions")).get(ingredient));
        assertNull(castMap(field(third, "resolutions")).get(ingredient));
    }

    @Test
    void pendingAggregateResolutionValidatesContextConsumesOnceAndResets() throws Exception {
        Class<?> scope = runtime.type(
                "io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$ResolutionScope");
        Class<?> treeType = runtime.type("dev.emi.emi.bom.MaterialTree");
        Object ingredient = runtime.stack("ore", 1);
        Object mismatch = runtime.stack("other", 1);
        Object resolution = runtime.recipe("test:resolution", runtime.stack("resolved", 1), List.of());
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:first", runtime.stack("first", 1), List.of(ingredient)));
        Object second = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:second", runtime.stack("second", 1), List.of(ingredient)));
        call(manager, "select", types(int.class), 0);

        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        assertTrue((boolean) call(manager, "hasPendingResolution", types()));
        assertFalse((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                second, ingredient, resolution));
        assertFalse((boolean) call(manager, "hasPendingResolution", types()));
        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        assertFalse((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                first, mismatch, resolution));
        assertFalse((boolean) call(manager, "hasPendingResolution", types()));
        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        assertTrue((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                first, ingredient, resolution));
        assertFalse((boolean) call(manager, "hasPendingResolution", types()));
        assertSame(resolution, castMap(field(first, "resolutions")).get(ingredient));
        assertSame(resolution, castMap(field(second, "resolutions")).get(ingredient));
        assertFalse((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                first, ingredient, null));
        assertSame(resolution, castMap(field(first, "resolutions")).get(ingredient));

        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        call(manager, "cancelPendingResolution", types());
        assertFalse((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                first, ingredient, null));

        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        call(manager, "replaceSolo", types("dev.emi.emi.api.recipe.EmiRecipe"),
                runtime.recipe("test:solo", runtime.stack("solo", 1), List.of()));
        assertFalse((boolean) call(manager, "hasPendingResolution", types()));

        Object selected = call(manager, "getSelectedTree", types());
        call(manager, "beginPendingResolution", types("dev.emi.emi.api.stack.EmiIngredient", scope),
                ingredient, enumConstant(scope, "ALL_ROOTS"));
        Class<?> bom = runtime.type("dev.emi.emi.bom.BoM");
        Field bomTree = bom.getField("tree");
        bomTree.set(null, newTree(runtime.recipe("test:external", runtime.stack("external", 1), List.of())));
        call(manager, "synchronizeSetGoal", types());
        assertFalse((boolean) call(manager, "hasPendingResolution", types()));
        assertNotSame(selected, call(manager, "getSelectedTree", types()));
    }

    @Test
    void reloadSnapshotRestoresRecoverableDuplicateStateAndSkipsMissingRoots() throws Exception {
        Object oldDuplicate = runtime.recipe("reload:duplicate", runtime.stack("duplicate-old", 1),
                List.of(runtime.stack("ingredient", 1)));
        Object missing = runtime.recipe("reload:missing", runtime.stack("missing", 1), List.of());
        Object oldLast = runtime.recipe("reload:last", runtime.stack("last-old", 1), List.of());
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), oldDuplicate);
        Object skipped = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), missing);
        Object selected = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), oldDuplicate);
        Object last = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), oldLast);
        field(first, "batches", 2L);
        field(skipped, "batches", 3L);
        field(selected, "batches", 4L);
        field(last, "batches", 5L);
        call(manager, "select", types(int.class), 2);
        call(manager, "setCraftingMode", types(boolean.class), true);

        Object ingredient = runtime.stack("ingredient", 1);
        Object oldResolution = runtime.recipe("reload:resolution", runtime.stack("resolved-old", 1), List.of());
        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", boolean.class), ingredient, oldResolution, false);
        Object selectedGoal = field(selected, "goal");
        Object selectedChild = ((List<?>) field(selectedGoal, "children")).get(0);
        field(selectedChild, "state", enumConstant(runtime.type("dev.emi.emi.bom.FoldState"), "COLLAPSED"));

        call(manager, "beginRecipeReload", types());
        assertTrue((boolean) call(manager, "isEmpty", types()));
        assertTrue((boolean) call(manager, "hasPendingRecipeReload", types()));

        Object newDuplicate = runtime.recipe("reload:duplicate", runtime.stack("duplicate-new", 1),
                List.of(runtime.stack("ingredient", 1)));
        Object newLast = runtime.recipe("reload:last", runtime.stack("last-new", 1), List.of());
        Object newResolution = runtime.recipe("reload:resolution", runtime.stack("resolved-new", 1), List.of());
        registerRecipe(newDuplicate);
        registerRecipe(newLast);
        registerRecipe(newResolution);
        call(manager, "finishRecipeReload", types());
        assertFalse((boolean) call(manager, "hasPendingRecipeReload", types()));

        List<?> restored = (List<?>) call(manager, "getTrees", types());
        assertEquals(3, restored.size());
        assertSame(newDuplicate, field(field(restored.get(0), "goal"), "recipe"));
        assertSame(newDuplicate, field(field(restored.get(1), "goal"), "recipe"));
        assertSame(newLast, field(field(restored.get(2), "goal"), "recipe"));
        assertEquals(2L, field(restored.get(0), "batches"));
        assertEquals(4L, field(restored.get(1), "batches"));
        assertEquals(5L, field(restored.get(2), "batches"));
        assertEquals(1, call(manager, "getSelectedIndex", types()));
        assertTrue((boolean) call(manager, "isCraftingMode", types()));
        Object restoredChild = ((List<?>) field(field(restored.get(1), "goal"), "children")).get(0);
        assertEquals("COLLAPSED", field(restoredChild, "state").toString());
        assertSame(newResolution, castMap(field(restored.get(1), "resolutions")).get(ingredient));
    }

    @Test
    void reloadSnapshotSurvivesRetryUntilSuccess() throws Exception {
        Object oldRecipe = runtime.recipe("reload:retry", runtime.stack("retry-old", 1), List.of());
        call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), oldRecipe);
        call(manager, "beginRecipeReload", types());
        call(manager, "beginRecipeReload", types());
        assertTrue((boolean) call(manager, "isEmpty", types()));

        Object currentRecipe = runtime.recipe("reload:retry", runtime.stack("retry-new", 1), List.of());
        registerRecipe(currentRecipe);
        call(manager, "finishRecipeReload", types());
        assertEquals(1, call(manager, "size", types()));
        assertSame(currentRecipe, field(field(call(manager, "getSelectedTree", types()), "goal"), "recipe"));
    }

    @Test
    void sessionClearDropsPendingReloadSnapshot() throws Exception {
        Object oldRecipe = runtime.recipe("reload:session-clear", runtime.stack("session-old", 1), List.of());
        call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), oldRecipe);
        call(manager, "beginRecipeReload", types());
        call(manager, "clear", types());
        assertFalse((boolean) call(manager, "hasPendingRecipeReload", types()));

        registerRecipe(runtime.recipe("reload:session-clear", runtime.stack("session-new", 1), List.of()));
        call(manager, "finishRecipeReload", types());
        assertTrue((boolean) call(manager, "isEmpty", types()));
    }

    @Test
    void costsShareRemaindersAndTrackChanceAndCraftingProgress() throws Exception {
        Object material = runtime.stack("material", 1);
        Object product = runtime.stack("product", 2);
        Object producerRecipe = runtime.recipe("test:producer", product, List.of(material));
        Object producer = newTree(producerRecipe);
        field(field(producer, "goal"), "amount", 1L);

        Object consumer = newTree(runtime.recipe("test:consumer", runtime.stack("unused", 1), List.of()));
        Object consumerGoal = runtime.type("dev.emi.emi.bom.MaterialNode")
                .getConstructor(runtime.type("dev.emi.emi.api.stack.EmiIngredient")).newInstance(runtime.stack("product", 1));
        field(consumer, "goal", consumerGoal);

        Object costs = call(runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestCosts"),
                "calculateNew", types(List.class, "dev.emi.emi.api.recipe.EmiPlayerInventory"),
                List.of(producer, consumer), null);
        Object total = call(costs.getClass(), costs, "getTotal", types());
        Map<?, ?> flatCosts = castMap(field(total, "costs"));
        assertEquals(1, flatCosts.size());
        assertEquals(1L, field(flatCosts.values().iterator().next(), "amount"));
        assertTrue(castMap(field(total, "remainders")).isEmpty());

        Object chanceInput = runtime.stack("chance", 1);
        chanceInput.getClass().getMethod("setChance", float.class).invoke(chanceInput, 0.5F);
        Object chanceTree = newTree(runtime.recipe("test:chance", runtime.stack("chance-output", 1),
                List.of(chanceInput)));
        Object chanceCosts = call(runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestCosts"),
                "calculateNew", types(List.class, "dev.emi.emi.api.recipe.EmiPlayerInventory"),
                List.of(chanceTree), null);
        Object chanceTotal = call(chanceCosts.getClass(), chanceCosts, "getTotal", types());
        Object chanceCost = castMap(field(chanceTotal, "chanceCosts")).values().iterator().next();
        assertEquals(0.5F, (float) field(chanceCost, "chance"), 0.0001F);

        Object needed = runtime.stack("needed", 2);
        Object progressTree = newTree(runtime.recipe("test:progress", runtime.stack("unused-progress", 1), List.of()));
        Object progressGoal = runtime.type("dev.emi.emi.bom.MaterialNode")
                .getConstructor(runtime.type("dev.emi.emi.api.stack.EmiIngredient")).newInstance(needed);
        field(progressTree, "goal", progressGoal);
        Object inventory = runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory").getConstructor(List.class)
                .newInstance(List.of(runtime.stack("needed", 1)));
        Object progressCosts = call(runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestCosts"),
                "calculateNew", types(List.class, "dev.emi.emi.api.recipe.EmiPlayerInventory"),
                List.of(progressTree), inventory);
        assertEquals("PARTIAL", field(progressGoal, "progress").toString());
        Object progress = call(progressCosts.getClass(), progressCosts, "getProgress", types());
        assertEquals(1L, field(castMap(field(progress, "costs")).values().iterator().next(), "amount"));
    }

    @Test
    void bookmarkJsonRoundTripSkipsCorruptRootsAndMissingRecipeWithoutReplacingForest() throws Exception {
        Class<?> jsonObject = runtime.type("com.google.gson.JsonObject");
        Class<?> jsonArray = runtime.type("com.google.gson.JsonArray");
        Object validRoot = jsonObject.getConstructor().newInstance();
        addProperty(validRoot, "recipe", "test:saved");
        addProperty(validRoot, "batches", 3);
        invoke(validRoot, "add", types(String.class, runtime.type("com.google.gson.JsonElement")),
                "resolutions", jsonArray.getConstructor().newInstance());
        Object folds = jsonObject.getConstructor().newInstance();
        addProperty(folds, "", "COLLAPSED");
        invoke(validRoot, "add", types(String.class, runtime.type("com.google.gson.JsonElement")), "folds", folds);
        Object corruptRoot = jsonObject.getConstructor().newInstance();
        addProperty(corruptRoot, "batches", "not-a-number");

        Object treeJson = jsonObject.getConstructor().newInstance();
        addProperty(treeJson, "name", "Saved forest");
        addProperty(treeJson, "selected", 4);
        addProperty(treeJson, "crafting", true);
        Object roots = jsonArray.getConstructor().newInstance();
        invoke(roots, "add", types(runtime.type("com.google.gson.JsonElement")), corruptRoot);
        invoke(roots, "add", types(runtime.type("com.google.gson.JsonElement")), validRoot);
        invoke(treeJson, "add", types(String.class, runtime.type("com.google.gson.JsonElement")), "roots", roots);

        Class<?> bookmark = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$TreeBookmark");
        Method fromJson = bookmark.getDeclaredMethod("fromJson", jsonObject);
        fromJson.setAccessible(true);
        Object restored = fromJson.invoke(null, treeJson);
        assertEquals("Saved forest", call(bookmark, restored, "name", types()));
        assertEquals(1, ((List<?>) field(restored, "roots")).size());
        Method toJson = bookmark.getDeclaredMethod("toJson");
        toJson.setAccessible(true);
        Object serialized = toJson.invoke(restored);
        Object roundTripped = fromJson.invoke(null, serialized);
        assertEquals("Saved forest", call(bookmark, roundTripped, "name", types()));
        assertEquals(1, ((List<?>) field(roundTripped, "roots")).size());

        Object existingRecipe = runtime.recipe("test:existing", runtime.stack("existing", 1), List.of());
        Object existingTree = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), existingRecipe);
        assertFalse((boolean) call(bookmark, restored, "apply", types()));
        assertEquals(1, call(manager, "size", types()));
        assertSame(existingTree, call(manager, "getSelectedTree", types()));
    }

    @Test
    void forestGridSettingsDefaultClampRoundTripAndPreserveCorruptFile() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        assertEquals(8, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(2, call(bookmarks, "getRootGridRows", types()));

        call(bookmarks, "setRootGridSize", types(int.class, int.class), 0, 99);
        assertEquals(1, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(8, call(bookmarks, "getRootGridRows", types()));
        call(bookmarks, "load", types());
        assertEquals(1, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(8, call(bookmarks, "getRootGridRows", types()));

        call(bookmarks, "setRootGridSize", types(int.class, int.class), 99, 0);
        call(bookmarks, "load", types());
        assertEquals(16, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(1, call(bookmarks, "getRootGridRows", types()));

        Path config = gameDirectory.resolve("config/emi_recipeforest.json");
        String corrupt = "{ definitely-not-json";
        Files.writeString(config, corrupt);
        call(bookmarks, "load", types());
        assertEquals(8, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(2, call(bookmarks, "getRootGridRows", types()));
        assertEquals(corrupt, Files.readString(config));
    }

    @Test
    void quantityDisplayHandlesBoxBoundariesStackSizesAndOverflow() {
        QuantityDisplay zero = QuantityDisplay.decompose(0, 64, true, 27, DisplayMode.ICON);
        assertFalse(zero.hasBoxes());
        assertFalse(zero.hasFullStacks());
        assertFalse(zero.hasItems());

        assertEquals(new QuantityDisplay(0, 26, 0, 64, 1728, DisplayMode.ICON),
                QuantityDisplay.decompose(26 * 64L, 64, true, 27, DisplayMode.ICON));
        assertEquals(new QuantityDisplay(1, 0, 0, 64, 1728, DisplayMode.ICON),
                QuantityDisplay.decompose(27 * 64L, 64, true, 27, DisplayMode.ICON));
        assertEquals(new QuantityDisplay(1, 1, 5, 64, 1728, DisplayMode.TEXT),
                QuantityDisplay.decompose(28 * 64L + 5, 64, true, 27, DisplayMode.TEXT));
        assertEquals(new QuantityDisplay(0, 28, 5, 64, 1728, DisplayMode.TEXT),
                QuantityDisplay.decompose(28 * 64L + 5, 64, false, 27, DisplayMode.TEXT));

        assertEquals(new QuantityDisplay(0, 2, 1, 16, 432, DisplayMode.ICON),
                QuantityDisplay.decompose(33, 16, false, 27, DisplayMode.ICON));
        assertEquals(64, QuantityDisplay.decompose(65, 64, false, 27, DisplayMode.ICON).stackUnitCapacity());
        assertEquals(new QuantityDisplay(0, 2, 50, 100, 2700, DisplayMode.TEXT),
                QuantityDisplay.decompose(250, 100, false, 27, DisplayMode.TEXT));

        QuantityDisplay saturated = QuantityDisplay.decompose(Long.MAX_VALUE, Long.MAX_VALUE,
                true, 2, DisplayMode.ICON);
        assertEquals(0, saturated.boxes());
        assertEquals(1, saturated.fullStacks());
        assertEquals(0, saturated.items());
        assertEquals(Long.MAX_VALUE, saturated.boxUnitCapacity());
    }

    @Test
    void optionalSettingsDefaultRoundTripAndRecoverUnknownValues() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        Class<?> resolutionScope = runtime.type(
                "io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$ResolutionScope");
        Class<?> rootLayout = runtime.type(
                "io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$RootLayout");
        Class<?> quantityMode = runtime.type(
                "io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$QuantityMode");

        assertEquals("ALL_ROOTS", call(bookmarks, "getResolutionScope", types()).toString());
        assertEquals("LIST", call(bookmarks, "getRootLayout", types()).toString());
        assertEquals("ICON", call(bookmarks, "getQuantityMode", types()).toString());
        assertEquals(70, call(bookmarks, "getForestKeyCode", types()));
        assertTrue((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(27, call(bookmarks, "getStacksPerBox", types()));
        assertEquals(64, call(bookmarks, "getListLength", types()));

        call(bookmarks, "setResolutionScope", types(resolutionScope), enumConstant(resolutionScope, "MATCHING_ROOTS"));
        call(bookmarks, "setRootLayout", types(rootLayout), enumConstant(rootLayout, "GRID"));
        call(bookmarks, "setQuantityMode", types(quantityMode), enumConstant(quantityMode, "TEXT"));
        call(bookmarks, "setForestKeyCode", types(int.class), 71);
        call(bookmarks, "setBoxEnabled", types(boolean.class), false);
        call(bookmarks, "setStacksPerBox", types(int.class), 999);
        call(bookmarks, "setListLength", types(int.class), 999);
        call(bookmarks, "load", types());

        assertEquals("MATCHING_ROOTS", call(bookmarks, "getResolutionScope", types()).toString());
        assertEquals("GRID", call(bookmarks, "getRootLayout", types()).toString());
        assertEquals("TEXT", call(bookmarks, "getQuantityMode", types()).toString());
        assertEquals(71, call(bookmarks, "getForestKeyCode", types()));
        assertFalse((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(256, call(bookmarks, "getStacksPerBox", types()));
        assertEquals(256, call(bookmarks, "getListLength", types()));

        call(bookmarks, "setStacksPerBox", types(int.class), -1);
        call(bookmarks, "load", types());
        assertEquals(1, call(bookmarks, "getStacksPerBox", types()));

        Path config = gameDirectory.resolve("config/emi_recipeforest.json");
        call(bookmarks, "setRootGridSize", types(int.class, int.class), 4, 3);
        Field gsonValues = runtime.type("com.google.gson.Gson").getDeclaredField("VALUES");
        gsonValues.setAccessible(true);
        Object root = ((Map<?, ?>) gsonValues.get(null)).get(Files.readString(config));
        Object settings = invoke(root, "getAsJsonObject", types(String.class), "settings");
        addProperty(settings, "resolutionScope", "FUTURE_SCOPE");
        addProperty(settings, "rootLayout", "FUTURE_LAYOUT");
        addProperty(settings, "quantityMode", "FUTURE_MODE");
        addProperty(settings, "forestBindings", "broken");
        addProperty(settings, "boxEnabled", "broken");
        addProperty(settings, "stacksPerBox", "broken");
        addProperty(settings, "listLength", "broken");
        call(bookmarks, "load", types());

        assertEquals(4, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(3, call(bookmarks, "getRootGridRows", types()));
        assertEquals("ALL_ROOTS", call(bookmarks, "getResolutionScope", types()).toString());
        assertEquals("LIST", call(bookmarks, "getRootLayout", types()).toString());
        assertEquals("ICON", call(bookmarks, "getQuantityMode", types()).toString());
        assertEquals(70, call(bookmarks, "getForestKeyCode", types()));
        assertTrue((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(27, call(bookmarks, "getStacksPerBox", types()));
        assertEquals(64, call(bookmarks, "getListLength", types()));
    }

    @Test
    void forestBindingsDefaultRoundTripLegacyMigrationAndCorruptionFallback() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        Class<?> binding = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$ForestBinding");
        Class<?> bindingType = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks$BindingType");

        List<?> defaults = (List<?>) call(bookmarks, "getForestBindings", types());
        assertEquals(1, defaults.size());
        assertBinding(defaults.get(0), "KEYSYM", "key.keyboard.f", 70, 0);

        List<Object> configured = List.of(
                newBinding(binding, bindingType, "KEYSYM", "key.keyboard.g", 71, 1),
                newBinding(binding, bindingType, "MOUSE", "key.mouse.left", 0, 4),
                newBinding(binding, bindingType, "SCANCODE", "scancode.30", 30, 2),
                newBinding(binding, bindingType, "KEYSYM", "key.keyboard.r", 82, 5));
        call(bookmarks, "setForestBindings", types(List.class), configured);
        call(bookmarks, "setRootGridSize", types(int.class, int.class), 6, 4);
        call(bookmarks, "load", types());
        assertEquals(configured, call(bookmarks, "getForestBindings", types()));
        assertEquals(6, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(4, call(bookmarks, "getRootGridRows", types()));

        call(bookmarks, "resetForestBindings", types());
        assertEquals(defaults, call(bookmarks, "getForestBindings", types()));

        Path config = gameDirectory.resolve("config/emi_recipeforest.json");
        Object legacyRoot = jsonObject();
        addProperty(legacyRoot, "schema", 1);
        Object legacySettings = jsonObject();
        addProperty(legacySettings, "forestKeyCode", 72);
        addProperty(legacySettings, "rootGridColumns", 5);
        addProperty(legacySettings, "rootGridRows", 3);
        addProperty(legacySettings, "boxEnabled", false);
        jsonAdd(legacyRoot, "settings", legacySettings);
        Object legacySearches = jsonArray();
        jsonArrayAdd(legacySearches, jsonPrimitive("legacy query"));
        jsonAdd(legacyRoot, "searches", legacySearches);
        jsonAdd(legacyRoot, "trees", jsonArray());
        Files.writeString(config, gsonString(legacyRoot));
        call(bookmarks, "load", types());
        List<?> migrated = (List<?>) call(bookmarks, "getForestBindings", types());
        assertEquals(2, migrated.size());
        assertBinding(migrated.get(0), "KEYSYM", "key.keyboard.72", 72, 0);
        assertBinding(migrated.get(1), "KEYSYM", "key.keyboard.72", 72, 4);
        assertEquals(5, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(3, call(bookmarks, "getRootGridRows", types()));
        assertFalse((boolean) call(bookmarks, "isBoxEnabled", types()));
        List<?> migratedSearches = (List<?>) call(bookmarks, "searches", types());
        assertEquals(1, migratedSearches.size());
        assertEquals("legacy query", invoke(migratedSearches.get(0), "query", types()));

        Field gsonValues = runtime.type("com.google.gson.Gson").getDeclaredField("VALUES");
        gsonValues.setAccessible(true);
        Object migratedRoot = ((Map<?, ?>) gsonValues.get(null)).get(Files.readString(config));
        assertEquals(2, invoke(invoke(migratedRoot, "get", types(String.class), "schema"),
                "getAsInt", types()));
        Object migratedSettings = invoke(migratedRoot, "getAsJsonObject", types(String.class), "settings");
        assertFalse((boolean) invoke(migratedSettings, "has", types(String.class), "forestKeyCode"));
        assertTrue((boolean) invoke(migratedSettings, "has", types(String.class), "forestBindings"));
        Object migratedBindings = invoke(migratedSettings, "getAsJsonArray", types(String.class), "forestBindings");
        assertEquals(2, invoke(migratedBindings, "size", types()));
        assertEquals(5, invoke(invoke(migratedSettings, "get", types(String.class), "rootGridColumns"),
                "getAsInt", types()));
        assertFalse((boolean) invoke(invoke(migratedSettings, "get", types(String.class), "boxEnabled"),
                "getAsBoolean", types()));
        Object rewrittenSearches = invoke(migratedRoot, "getAsJsonArray", types(String.class), "searches");
        assertEquals("legacy query", invoke(invoke(rewrittenSearches, "get", types(int.class), 0),
                "getAsString", types()));

        Object brokenLegacyRoot = jsonObject();
        addProperty(brokenLegacyRoot, "schema", 1);
        Object brokenLegacySettings = jsonObject();
        addProperty(brokenLegacySettings, "forestKeyCode", "broken");
        jsonAdd(brokenLegacyRoot, "settings", brokenLegacySettings);
        jsonAdd(brokenLegacyRoot, "searches", jsonArray());
        jsonAdd(brokenLegacyRoot, "trees", jsonArray());
        String brokenLegacyJson = gsonString(brokenLegacyRoot);
        Files.writeString(config, brokenLegacyJson);
        call(bookmarks, "load", types());
        assertEquals(brokenLegacyJson, Files.readString(config));
        assertEquals(defaults, call(bookmarks, "getForestBindings", types()));

        Object corruptRoot = jsonObject();
        addProperty(corruptRoot, "schema", 2);
        Object corruptSettings = jsonObject();
        Object corruptBindings = jsonArray();
        Object corruptBinding = jsonObject();
        addProperty(corruptBinding, "type", "FUTURE");
        addProperty(corruptBinding, "name", "");
        addProperty(corruptBinding, "value", -1);
        addProperty(corruptBinding, "modifiers", 64);
        jsonArrayAdd(corruptBindings, corruptBinding);
        jsonAdd(corruptSettings, "forestBindings", corruptBindings);
        jsonAdd(corruptRoot, "settings", corruptSettings);
        jsonAdd(corruptRoot, "searches", jsonArray());
        jsonAdd(corruptRoot, "trees", jsonArray());
        Files.writeString(config, gsonString(corruptRoot));
        call(bookmarks, "load", types());
        assertEquals(defaults, call(bookmarks, "getForestBindings", types()));
    }

    @Test
    void nativeForestBindMatchesPersistsReloadsResetsAndReportsCollisions() throws Exception {
        Class<?> bookmarks = runtime.type("io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks");
        Class<?> forestBindType = runtime.type("io.github.mango_clark.emirecipeforest.input.ForestBind");
        Class<?> modifiedKey = runtime.type("dev.emi.emi.input.EmiBind$ModifiedKey");
        Class<?> inputType = runtime.type("com.mojang.blaze3d.platform.InputConstants$Type");
        Class<?> inputKey = runtime.type("com.mojang.blaze3d.platform.InputConstants$Key");
        Class<?> emiInput = runtime.type("dev.emi.emi.input.EmiInput");
        Object bind = forestBindType.getField("INSTANCE").get(null);

        call(bookmarks, "resetForestBindings", types());
        call(forestBindType, bind, "reloadFromBookmarks", types());
        call(emiInput, "setCurrentModifiers", types(int.class), 0);
        assertTrue((boolean) call(forestBindType, bind, "matchesKey", types(int.class, int.class), 70, 0));
        call(emiInput, "setCurrentModifiers", types(int.class), 4);
        assertFalse((boolean) call(forestBindType, bind, "matchesKey", types(int.class, int.class), 70, 0));
        call(emiInput, "setCurrentModifiers", types(int.class), 1);
        assertFalse((boolean) call(forestBindType, bind, "matchesKey", types(int.class, int.class), 70, 0));

        Object keyG = modifiedKey(modifiedKey, inputType, inputKey, "KEYSYM", 71, 1);
        Object mouseLeft = modifiedKey(modifiedKey, inputType, inputKey, "MOUSE", 0, 4);
        Object scan30 = modifiedKey(modifiedKey, inputType, inputKey, "SCANCODE", 30, 2);
        Object keyR = modifiedKey(modifiedKey, inputType, inputKey, "KEYSYM", 82, 5);
        Object ignoredFifth = modifiedKey(modifiedKey, inputType, inputKey, "KEYSYM", 70, 0);
        Object array = java.lang.reflect.Array.newInstance(modifiedKey, 5);
        for (int i = 0; i < 5; i++) {
            java.lang.reflect.Array.set(array, i, List.of(keyG, mouseLeft, scan30, keyR, ignoredFifth).get(i));
        }
        call(forestBindType, bind, "setBinds", types(array.getClass()), array);
        assertEquals(4, ((List<?>) call(bookmarks, "getForestBindings", types())).size());
        Path addonConfig = gameDirectory.resolve("config/emi_recipeforest.json");
        assertTrue(Files.isRegularFile(addonConfig));
        assertFalse(Files.exists(gameDirectory.resolve("config/emi.json")));
        assertFalse(Files.exists(gameDirectory.resolve("config/emi.css")));

        call(emiInput, "setCurrentModifiers", types(int.class), 4);
        assertTrue((boolean) call(forestBindType, bind, "matchesMouse", types(int.class), 0));
        call(emiInput, "setCurrentModifiers", types(int.class), 2);
        assertTrue((boolean) call(forestBindType, bind, "matchesKey", types(int.class, int.class), -1, 30));

        String beforeReload = Files.readString(addonConfig);
        call(forestBindType, bind, "reloadFromBookmarks", types());
        assertEquals(beforeReload, Files.readString(addonConfig));

        Object keyA = modifiedKey(modifiedKey, inputType, inputKey, "KEYSYM", 65, 0);
        call(forestBindType, bind, "setBind", types(int.class, modifiedKey), 0, keyA);
        List<?> collisions = (List<?>) call(forestBindType, bind, "getCollisions", types());
        assertTrue(collisions.stream().anyMatch(collision -> {
            try {
                return "binds.favorite".equals(invoke(collision, "configKey", types()));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }));
        assertFalse(collisions.stream().anyMatch(collision -> {
            try {
                return "ui.not-a-bind".equals(invoke(collision, "configKey", types()));
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }));

        call(forestBindType, bind, "setToDefault", types());
        List<?> reset = (List<?>) call(bookmarks, "getForestBindings", types());
        assertEquals(1, reset.size());
        assertBinding(reset.get(0), "KEYSYM", "key.keyboard.f", 70, 0);
    }

    private static Object newTree(Object recipe) throws Exception {
        return runtime.type("dev.emi.emi.bom.MaterialTree")
                .getConstructor(runtime.type("dev.emi.emi.api.recipe.EmiRecipe")).newInstance(recipe);
    }

    private static void registerRecipe(Object recipe) throws Exception {
        Object recipeManager = call(runtime.type("dev.emi.emi.api.EmiApi"), "getRecipeManager", types());
        call(recipeManager.getClass(), recipeManager, "put", types("dev.emi.emi.api.recipe.EmiRecipe"), recipe);
    }

    private static void clearResolutions(Object... trees) throws Exception {
        for (Object tree : trees) {
            castMap(field(tree, "resolutions")).clear();
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Class<?>[] types(Object... names) throws Exception {
        Class<?>[] result = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = names[i] instanceof Class<?> type ? type : runtime.type((String) names[i]);
        }
        return result;
    }

    private static Object call(Class<?> owner, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        return call(owner, null, name, parameterTypes, args);
    }

    private static Object call(Class<?> owner, Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        return owner.getMethod(name, parameterTypes).invoke(target, args);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        return call(target.getClass(), target, name, parameterTypes, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void addProperty(Object json, String name, Object value) throws Exception {
        Class<?> parameter = value instanceof Number ? Number.class : value instanceof Boolean ? Boolean.class : String.class;
        invoke(json, "addProperty", types(String.class, parameter), name, value);
    }

    private static Object newBinding(Class<?> binding, Class<?> bindingType, String type, String name,
            int value, int modifiers) throws Exception {
        return binding.getConstructor(bindingType, String.class, int.class, int.class)
                .newInstance(enumConstant(bindingType, type), name, value, modifiers);
    }

    private static Object modifiedKey(Class<?> modifiedKey, Class<?> inputType, Class<?> inputKey,
            String type, int value, int modifiers) throws Exception {
        Object keyType = enumConstant(inputType, type);
        Object key = call(inputType, keyType, "getOrCreate", types(int.class), value);
        return modifiedKey.getConstructor(inputKey, int.class).newInstance(key, modifiers);
    }

    private static void assertBinding(Object binding, String type, String name, int value, int modifiers)
            throws Exception {
        assertEquals(type, invoke(binding, "type", types()).toString());
        assertEquals(name, invoke(binding, "name", types()));
        assertEquals(value, invoke(binding, "value", types()));
        assertEquals(modifiers, invoke(binding, "modifiers", types()));
    }

    private static Object jsonObject() throws Exception {
        return runtime.type("com.google.gson.JsonObject").getConstructor().newInstance();
    }

    private static Object jsonArray() throws Exception {
        return runtime.type("com.google.gson.JsonArray").getConstructor().newInstance();
    }

    private static Object jsonPrimitive(Object value) throws Exception {
        return runtime.type("com.google.gson.JsonPrimitive").getConstructor(Object.class).newInstance(value);
    }

    private static void jsonAdd(Object object, String name, Object value) throws Exception {
        invoke(object, "add", types(String.class, "com.google.gson.JsonElement"), name, value);
    }

    private static void jsonArrayAdd(Object array, Object value) throws Exception {
        invoke(array, "add", types("com.google.gson.JsonElement"), value);
    }

    private static String gsonString(Object value) throws Exception {
        Object gson = runtime.type("com.google.gson.Gson").getConstructor().newInstance();
        return (String) invoke(gson, "toJson", types("com.google.gson.JsonElement"), value);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(Object value) {
        return (Map<Object, Object>) value;
    }
}
