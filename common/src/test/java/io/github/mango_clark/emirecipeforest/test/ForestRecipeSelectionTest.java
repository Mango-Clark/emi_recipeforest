package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForestRecipeSelectionTest {
    private static IsolatedEmiRuntime runtime;

    @BeforeAll
    static void openRuntime() throws Exception {
        runtime = new IsolatedEmiRuntime();
    }

    @AfterAll
    static void closeRuntime() throws Exception {
        runtime.close();
    }

    @Test
    void emptyListDoesNotRequestRandomNumbers() throws Exception {
        assertNull(choose(List.of(), bound -> { throw new AssertionError("No eligible recipe"); }));
    }

    @Test
    void unsupportedListIsVisitedExactlyOnce() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Object unsupported = recipe(false, checks);
        assertNull(choose(Collections.nCopies(100_001, unsupported),
                bound -> { throw new AssertionError("No eligible recipe"); }));
        assertEquals(100_001, checks.get());
    }

    @Test
    void oneSupportedRecipeIsAlwaysSelected() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Object supported = recipe(true, checks);
        assertSame(supported, choose(List.of(supported),
                bound -> { throw new AssertionError("One eligible recipe needs no sampling"); }));
        assertEquals(1, checks.get());
    }

    @Test
    void mixedListCountsOnlySupportedRecipesForSampling() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Object unsupported = recipe(false, checks);
        Object first = recipe(true, checks);
        Object second = recipe(true, checks);
        AtomicInteger randomCalls = new AtomicInteger();
        assertSame(second, choose(List.of(unsupported, first, unsupported, second, unsupported), bound -> {
            assertEquals(2, bound);
            randomCalls.incrementAndGet();
            return 0;
        }));
        assertEquals(5, checks.get());
        assertEquals(1, randomCalls.get());
        assertSame(first, choose(List.of(first, unsupported, second), bound -> bound - 1));
    }

    @Test
    void rareSupportedRecipeCannotBeMissed() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        Object unsupported = recipe(false, checks);
        Object supported = recipe(true, checks);
        List<Object> recipes = new java.util.AbstractList<>() {
            public int size() { return 200_001; }
            public Object get(int index) { return index == 200_000 ? supported : unsupported; }
        };
        assertSame(supported, choose(recipes, bound -> { throw new AssertionError("Only one eligible recipe"); }));
        assertEquals(200_001, checks.get());
    }

    private static Object recipe(boolean supported, AtomicInteger checks) throws Exception {
        Class<?> type = runtime.type("dev.emi.emi.api.recipe.EmiRecipe");
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("supportsRecipeTree")) {
                checks.incrementAndGet();
                return supported;
            }
            throw new AssertionError(method.getName());
        });
    }

    private static Object choose(List<?> recipes, IntUnaryOperator random) throws Exception {
        return runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestRecipeSelection")
                .getMethod("choose", List.class, IntUnaryOperator.class).invoke(null, recipes, random);
    }
}
