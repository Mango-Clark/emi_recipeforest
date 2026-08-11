package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForestBehaviorTest {
    private static IsolatedEmiRuntime runtime;
    private static Class<?> manager;

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
        call(manager, "clear", types());
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
        Object firstRecipe = runtime.recipe("test:first", runtime.stack("first", 1), List.of());
        Object secondRecipe = runtime.recipe("test:second", runtime.stack("second", 1), List.of());
        Object resolution = runtime.recipe("test:resolution", runtime.stack("resolved", 1), List.of());
        Object ingredient = runtime.stack("ore", 1);
        Object first = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), firstRecipe);
        Object second = call(manager, "add", types("dev.emi.emi.api.recipe.EmiRecipe"), secondRecipe);
        call(manager, "select", types(int.class), 0);

        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", boolean.class), ingredient, resolution, false);
        assertEquals(1, ((Map<?, ?>) field(first, "resolutions")).size());
        assertTrue(((Map<?, ?>) field(second, "resolutions")).isEmpty());

        call(manager, "addResolution", types("dev.emi.emi.api.stack.EmiIngredient",
                "dev.emi.emi.api.recipe.EmiRecipe", boolean.class), ingredient, resolution, true);
        assertEquals(1, ((Map<?, ?>) field(first, "resolutions")).size());
        assertEquals(1, ((Map<?, ?>) field(second, "resolutions")).size());
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

    private static Object newTree(Object recipe) throws Exception {
        return runtime.type("dev.emi.emi.bom.MaterialTree")
                .getConstructor(runtime.type("dev.emi.emi.api.recipe.EmiRecipe")).newInstance(recipe);
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
