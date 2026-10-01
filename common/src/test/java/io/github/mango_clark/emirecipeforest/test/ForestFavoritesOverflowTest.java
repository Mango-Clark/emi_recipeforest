package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForestFavoritesOverflowTest {
    private static IsolatedEmiRuntime runtime;
    private static Class<?> manager;
    private static Class<?> forestFavorites;
    private static Class<?> inventoryType;

    @BeforeAll
    static void openRuntime() throws Exception {
        runtime = new IsolatedEmiRuntime();
        manager = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestManager");
        forestFavorites = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestFavorites");
        inventoryType = runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory");
    }

    @AfterAll
    static void closeRuntime() throws Exception {
        runtime.close();
    }

    @BeforeEach
    void resetForest() throws Exception {
        call(manager, null, "clear", new Class<?>[0]);
        favorites().clear();
    }

    @Test
    void inventoryOverflowPreservesFavoritesAndPreviouslyPublishedNativeProgress() throws Exception {
        Object recipe = runtime.recipe("test:inventory-overflow", runtime.stack("product", 1),
                List.of(runtime.stack("material", 1)));
        Object tree = tree(recipe);
        set(tree, "batches", Long.MAX_VALUE);
        Object goal = get(tree, "goal");
        Object child = ((List<?>) get(goal, "children")).get(0);
        set(child, "remainder", runtime.stack("bucket", 1));
        set(child, "remainderAmount", 1L);
        useForest(List.of(tree));

        update(inventory(runtime.stack("material", 1)));
        assertEquals("PARTIAL", get(child, "progress").toString());
        List<?> before = List.copyOf(favorites());
        assertFalse(before.isEmpty());
        Object goalProgress = get(goal, "progress");
        Object childProgress = get(child, "progress");
        long totalNeeded = (long) get(goal, "totalNeeded");
        long neededBatches = (long) get(goal, "neededBatches");

        // The returned bucket count fits alone; adding one inventory bucket does not.
        update(inventory(runtime.stack("material", 1), runtime.stack("bucket", 1)));

        assertEquals(before.size(), favorites().size());
        for (int i = 0; i < before.size(); i++) {
            assertSame(before.get(i), favorites().get(i));
        }
        assertSame(goalProgress, get(goal, "progress"));
        assertSame(childProgress, get(child, "progress"));
        assertEquals(totalNeeded, get(goal, "totalNeeded"));
        assertEquals(neededBatches, get(goal, "neededBatches"));
        assertTrue((boolean) call(manager, null, "isCraftingMode", new Class<?>[0]));
    }

    @Test
    void sharedCatalystRecipeUsesSingleCraftStateWhenAggregatedInputProductOverflows() throws Exception {
        Object recipe = runtime.recipe("test:shared-catalyst", runtime.stack("product", 1),
                List.of(runtime.stack("catalyst", 2)));
        Object first = tree(recipe);
        Object second = tree(recipe);
        for (Object tree : List.of(first, second)) {
            set(tree, "batches", Long.MAX_VALUE / 2);
            Object child = ((List<?>) get(get(tree, "goal"), "children")).get(0);
            set(child, "catalyst", true);
        }
        useForest(List.of(first, second));
        Object inventory = inventory(runtime.stack("catalyst", 2));
        Class<?> recipeType = runtime.type("dev.emi.emi.api.recipe.EmiRecipe");
        // This double retains the verified native bug: 2 * (MAX - 1) becomes -4.
        assertTrue((boolean) call(inventoryType, inventory, "canCraft", new Class<?>[]{recipeType, long.class},
                recipe, Long.MAX_VALUE - 1));

        update(inventory);

        assertEquals(1, favorites().size());
        Object favorite = favorites().get(0);
        assertSame(recipe, get(favorite, "recipe"));
        assertEquals(Long.MAX_VALUE - 1, get(favorite, "batches"));
        assertEquals(Long.MAX_VALUE - 1, get(favorite, "amount"));
        assertEquals(1, get(favorite, "state"));
    }

    @Test
    void representableBulkAndSingleCraftStatesStillUseNativeInventoryChecks() throws Exception {
        Object recipe = runtime.recipe("test:normal", runtime.stack("product", 1),
                List.of(runtime.stack("material", 2)));
        Object tree = tree(recipe);
        set(tree, "batches", 2L);
        useForest(List.of(tree));

        update(inventory(runtime.stack("material", 4)));
        assertEquals(2, get(favorites().get(0), "state"));
        update(inventory(runtime.stack("material", 2)));
        assertEquals(1, get(favorites().get(0), "state"));
        update(inventory());
        assertEquals(0, get(favorites().get(0), "state"));
    }

    private static void useForest(List<?> trees) throws Exception {
        assertTrue((boolean) call(manager, null, "replaceTrees",
                new Class<?>[]{List.class, int.class, boolean.class}, trees, 0, true));
    }

    private static Object tree(Object recipe) throws Exception {
        return runtime.type("dev.emi.emi.bom.MaterialTree")
                .getConstructor(runtime.type("dev.emi.emi.api.recipe.EmiRecipe")).newInstance(recipe);
    }

    private static Object inventory(Object... stacks) throws Exception {
        return inventoryType.getConstructor(List.class).newInstance(List.of(stacks));
    }

    private static void update(Object inventory) throws Exception {
        call(forestFavorites, null, "updateSynthetic", new Class<?>[]{inventoryType}, inventory);
    }

    private static List<?> favorites() throws Exception {
        return (List<?>) runtime.type("dev.emi.emi.runtime.EmiFavorites").getField("syntheticFavorites").get(null);
    }

    private static Object get(Object owner, String field) throws Exception {
        return owner.getClass().getField(field).get(owner);
    }

    private static void set(Object owner, String field, Object value) throws Exception {
        owner.getClass().getField(field).set(owner, value);
    }

    private static Object call(Class<?> type, Object owner, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = type.getMethod(name, types);
        try {
            return method.invoke(owner, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
