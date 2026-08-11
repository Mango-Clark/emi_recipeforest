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
        assertFalse((boolean) call(manager, "tryApplyPendingResolution", types(treeType,
                "dev.emi.emi.api.stack.EmiIngredient", "dev.emi.emi.api.recipe.EmiRecipe"),
                first, mismatch, resolution));
        assertTrue((boolean) call(manager, "hasPendingResolution", types()));
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

        call(bookmarks, "setResolutionScope", types(resolutionScope), enumConstant(resolutionScope, "MATCHING_ROOTS"));
        call(bookmarks, "setRootLayout", types(rootLayout), enumConstant(rootLayout, "GRID"));
        call(bookmarks, "setQuantityMode", types(quantityMode), enumConstant(quantityMode, "TEXT"));
        call(bookmarks, "setForestKeyCode", types(int.class), 71);
        call(bookmarks, "setBoxEnabled", types(boolean.class), false);
        call(bookmarks, "setStacksPerBox", types(int.class), 999);
        call(bookmarks, "load", types());

        assertEquals("MATCHING_ROOTS", call(bookmarks, "getResolutionScope", types()).toString());
        assertEquals("GRID", call(bookmarks, "getRootLayout", types()).toString());
        assertEquals("TEXT", call(bookmarks, "getQuantityMode", types()).toString());
        assertEquals(71, call(bookmarks, "getForestKeyCode", types()));
        assertFalse((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(256, call(bookmarks, "getStacksPerBox", types()));

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
        addProperty(settings, "forestKeyCode", "broken");
        addProperty(settings, "boxEnabled", "broken");
        addProperty(settings, "stacksPerBox", "broken");
        call(bookmarks, "load", types());

        assertEquals(4, call(bookmarks, "getRootGridColumns", types()));
        assertEquals(3, call(bookmarks, "getRootGridRows", types()));
        assertEquals("ALL_ROOTS", call(bookmarks, "getResolutionScope", types()).toString());
        assertEquals("LIST", call(bookmarks, "getRootLayout", types()).toString());
        assertEquals("ICON", call(bookmarks, "getQuantityMode", types()).toString());
        assertEquals(70, call(bookmarks, "getForestKeyCode", types()));
        assertTrue((boolean) call(bookmarks, "isBoxEnabled", types()));
        assertEquals(27, call(bookmarks, "getStacksPerBox", types()));
    }

    private static Object newTree(Object recipe) throws Exception {
        return runtime.type("dev.emi.emi.bom.MaterialTree")
                .getConstructor(runtime.type("dev.emi.emi.api.recipe.EmiRecipe")).newInstance(recipe);
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

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(Object value) {
        return (Map<Object, Object>) value;
    }
}
