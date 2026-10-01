package io.github.mango_clark.emirecipeforest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ForestCostsOverflowTest {
    private static IsolatedEmiRuntime runtime;
    private static Class<?> amounts;
    private static Class<?> costsType;

    @BeforeAll
    static void openRuntime() throws Exception {
        runtime = new IsolatedEmiRuntime();
        amounts = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestAmounts");
        costsType = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestCosts");
    }

    @AfterAll
    static void closeRuntime() throws Exception {
        runtime.close();
    }

    @Test
    void batchModifiersPreserveConventionsAndRejectOverflow() throws Exception {
        assertEquals(16L, adjust(1, 1, true, false));
        assertEquals(32L, adjust(16, 1, false, true));
        assertEquals(3L, adjust(5, -1, false, true));
        assertEquals(1L, adjust(5, -16, false, false));
        assertEquals(5L, adjust(5, 0, false, true));
        assertThrows(ArithmeticException.class, () -> adjust(Long.MAX_VALUE, 1, false, false));
        assertThrows(ArithmeticException.class, () -> adjust(1L << 62, 1, false, true));
        assertThrows(ArithmeticException.class, () -> adjust(1, Long.MAX_VALUE, true, false));
    }

    @Test
    void integerCeilingAndDisplayQuantitiesRemainExactBeyondFloatAndDoubleLimits() throws Exception {
        long aboveDouble = (1L << 53) + 1;
        assertEquals(aboveDouble, ceil(aboveDouble, 1));
        assertEquals((1L << 52) + 1, ceil(aboveDouble, 2));
        assertEquals(Long.MAX_VALUE, ceil(Long.MAX_VALUE, 1));
        assertEquals((1L << 62), ceil(Long.MAX_VALUE, 2));
        assertEquals(1L << 32, call(amounts, null, "roundExpected",
                new Class<?>[]{long.class, float.class}, 1L << 33, 0.5F));
        assertEquals(Long.MAX_VALUE, call(amounts, null, "roundExpected",
                new Class<?>[]{long.class, float.class}, Long.MAX_VALUE, 1F));
        assertThrows(ArithmeticException.class, () -> ceil(1, 0));
    }

    @Test
    void rootProductAcceptsBoundaryAndRejectsOverflow() throws Exception {
        Object tree = leaf("root", 1);
        set(tree, "batches", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, amount(first(result(calculate(List.of(tree), null), "getTotal"), "costs")));
        set(get(tree, "goal"), "amount", 2L);
        assertInvalid(List.of(tree));
    }

    @Test
    void childAndOutputRoundingProductsRejectOverflow() throws Exception {
        Object childProduct = tree("child", 1, runtime.stack("input", 2));
        set(childProduct, "batches", Long.MAX_VALUE);
        assertInvalid(List.of(childProduct));

        Object roundedOutput = tree("round", 2);
        set(get(roundedOutput, "goal"), "amount", 1L);
        set(roundedOutput, "batches", Long.MAX_VALUE);
        assertInvalid(List.of(roundedOutput));
    }

    @Test
    void recipeCeilingAtTwoToThe53RetainsTheOddUnit() throws Exception {
        Object tree = tree("ceil", 1, runtime.stack("input", 1));
        long count = (1L << 53) + 1;
        set(tree, "batches", count);
        Object costs = calculate(List.of(tree), null);
        assertEquals(count, amount(first(result(costs, "getTotal"), "costs")));
        assertEquals(count, get(get(tree, "goal"), "neededBatches"));
    }

    @Test
    void sharedMaterialSumAcceptsExactMaximumAndRejectsOneMore() throws Exception {
        Object first = leaf("shared", 1);
        Object second = leaf("shared", 1);
        set(first, "batches", Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, amount(first(result(calculate(List.of(first, second), null), "getTotal"), "costs")));
        set(second, "batches", 2L);
        assertInvalid(List.of(first, second));
    }

    @Test
    void chanceCostsRoundAsLongAndMergeExpectedAmountsPrecisely() throws Exception {
        Object first = tree("chance-first", 1, chanceStack("shared-chance", 1, 0.5F));
        Object second = tree("chance-second", 1, chanceStack("shared-chance", 1, 0.25F));
        set(first, "batches", (1L << 53) + 1);
        set(second, "batches", 2L);
        Object chance = first(result(calculate(List.of(first, second), null), "getTotal"), "chanceCosts");
        assertEquals((1L << 52) + 1, effective(chance));
        assertEquals((1L << 53) + 3, amount(chance));
    }

    @Test
    void chanceCostMergeAndExpectedExpansionRejectOverflow() throws Exception {
        Object first = tree("chance-a", 1, chanceStack("common", 1, 0.5F));
        Object second = tree("chance-b", 1, chanceStack("common", 1, 0.5F));
        set(first, "batches", 1L << 62);
        set(second, "batches", 1L << 62);
        assertInvalid(List.of(first, second));

        Object expanded = treeWithOutput("expanded", chanceStack("expanded", 1, 0.5F), runtime.stack("input", 1));
        set(expanded, "batches", 1L << 62);
        assertInvalid(List.of(expanded));
    }

    @Test
    void chanceProgressDifferencePreservesSingleUnitsAtLargeQuantities() throws Exception {
        Object tree = tree("chance-progress", 1, chanceStack("input", 1, 0.5F));
        set(tree, "batches", (1L << 53) + 1);
        Object inventory = inventory(runtime.stack("input", 1));
        Object costs = calculate(List.of(tree), inventory);
        Object total = first(result(costs, "getTotal"), "chanceCosts");
        Object progress = first(result(costs, "getProgress"), "chanceCosts");
        Class<?> flat = runtime.type("dev.emi.emi.bom.FlatMaterialCost");
        assertEquals(1L, call(costsType, null, "completedAmount", new Class<?>[]{flat, flat}, total, progress));
    }

    @Test
    void progressDifferenceDoesNotRoundUpDivisionResiduals() throws Exception {
        Class<?> flat = runtime.type("dev.emi.emi.bom.FlatMaterialCost");
        for (float probability : new float[]{0.3F, 0.7F, 0.9F}) {
            Object tree = treeWithOutput("division-progress", chanceStack("division-output", 1, probability),
                    runtime.stack("division-input", 1));
            set(tree, "batches", 5L);
            Object costs = calculate(List.of(tree), inventory(runtime.stack("division-input", 1)));
            Object total = first(result(costs, "getTotal"), "chanceCosts");
            Object progress = first(result(costs, "getProgress"), "chanceCosts");
            assertEquals(1L, call(costsType, null, "completedAmount", new Class<?>[]{flat, flat}, total, progress));
        }
    }

    @Test
    void equalOutputAndInputChancesCancelBeforePartialConsumption() throws Exception {
        Class<?> flat = runtime.type("dev.emi.emi.bom.FlatMaterialCost");
        for (float probability : new float[]{0.1F, 0.2F}) {
            Object tree = treeWithOutput("cancel-progress", chanceStack("cancel-output", 1, probability),
                    chanceStack("cancel-input", 1, probability));
            set(tree, "batches", 5L);
            Object costs = calculate(List.of(tree), inventory(runtime.stack("cancel-input", 1)));
            Object total = first(result(costs, "getTotal"), "chanceCosts");
            Object remaining = first(result(costs, "getProgress"), "chanceCosts");
            assertEquals(5L, effective(total), "total at " + probability);
            assertEquals(4L, effective(remaining), "remaining at " + probability);
            assertEquals(1L, call(costsType, null, "completedAmount", new Class<?>[]{flat, flat}, total, remaining),
                    "completed at " + probability);
        }
    }

    @Test
    void equalOutputAndInputChancesAcceptMaximumBatchCount() throws Exception {
        for (float probability : new float[]{0.1F, 0.2F}) {
            Object tree = treeWithOutput("cancel-maximum", chanceStack("cancel-output", 1, probability),
                    chanceStack("cancel-input", 1, probability));
            set(tree, "batches", Long.MAX_VALUE);
            call(costsType, null, "validateAmounts", new Class<?>[]{List.class, Map.class}, List.of(tree), Map.of());
            Object costs = calculate(List.of(tree), null);
            assertEquals(Long.MAX_VALUE, effective(first(result(costs, "getTotal"), "chanceCosts")));
            assertEquals(Long.MAX_VALUE, effective(first(result(costs, "getProgress"), "chanceCosts")));
        }
    }

    @Test
    void productionChanceNearMaximumHasMatchingCostAndDisplayAmount() throws Exception {
        Object tree = treeWithOutput("display-boundary", chanceStack("display-output", 1, 0.3F),
                runtime.stack("display-input", 1));
        long batches = 2767011721007595519L;
        long expected = 9223372036854775805L;
        set(tree, "batches", batches);
        Object total = result(calculate(List.of(tree), null), "getTotal");
        assertEquals(expected, effective(first(total, "chanceCosts")));
        assertEquals(expected, displayExpected(batches, chance(0.3F, 1F)));
    }

    @Test
    void cancelledChancesRetainChanceDisplayAndMaximumQuantity() throws Exception {
        for (float probability : new float[]{0.1F, 0.2F}) {
            Object chance = chance(probability, probability);
            assertTrue((boolean) call(chance.getClass(), chance, "chanced", new Class<?>[0]));
            assertEquals(Long.MAX_VALUE, displayExpected(Long.MAX_VALUE, chance));
            assertEquals(5L, displayExpected(5, chance));
        }
    }

    @Test
    void exactChanceDisplayRetainsNearestRoundingAndRejectsRealOverflow() throws Exception {
        assertEquals(1L, displayExpected(1, chance(1F, 0.5F)));
        assertEquals(2L, displayExpected(3, chance(1F, 0.5F)));
        assertEquals(0L, displayExpected(Long.MAX_VALUE, chance(1F, 0F)));
        assertThrows(ArithmeticException.class, () -> displayExpected(Long.MAX_VALUE, chance(0.3F, 1F)));
        assertThrows(ArithmeticException.class, () -> chance(0F, 1F));
        assertThrows(ArithmeticException.class, () -> chance(Float.NaN, 1F));
        assertThrows(ArithmeticException.class, () -> chance(1F, Float.POSITIVE_INFINITY));
        assertThrows(ArithmeticException.class, () -> chance(1F, -0.1F));
    }

    @Test
    void returnedMaterialProductAndSumRejectOverflow() throws Exception {
        Object product = tree("returned-product", 1, runtime.stack("input", 1));
        remainder(product, "bucket", Long.MAX_VALUE);
        set(product, "batches", 2L);
        assertInvalid(List.of(product));

        Object first = tree("returned-a", 1, runtime.stack("input-a", 1));
        Object second = tree("returned-b", 1, runtime.stack("input-b", 1));
        remainder(first, "bucket", 1);
        remainder(second, "bucket", 1);
        set(first, "batches", Long.MAX_VALUE - 1);
        assertEquals(Long.MAX_VALUE, amount(first(result(calculate(List.of(first, second), null), "getTotal"), "remainders")));
        set(second, "batches", 2L);
        assertInvalid(List.of(first, second));
    }

    @Test
    void chanceRemaindersKeepLargeExpectedCountsAndRejectMergeOverflow() throws Exception {
        Object first = tree("chance-return-a", 1, chanceStack("input-a", 1, 0.5F));
        Object second = tree("chance-return-b", 1, chanceStack("input-b", 1, 0.5F));
        remainder(first, "chance-bucket", 1);
        remainder(second, "chance-bucket", 1);
        set(first, "batches", 1L << 33);
        Object costs = calculate(List.of(first), null);
        assertEquals(1L << 32, effective(first(result(costs, "getTotal"), "chanceRemainders")));
        set(first, "batches", 1L << 62);
        set(second, "batches", 1L << 62);
        assertInvalid(List.of(first, second));

        Object expanded = treeWithOutput("chance-return-expanded",
                chanceStack("chance-return-expanded", 1, 0.5F), runtime.stack("expanded-input", 1));
        remainder(expanded, "expanded-bucket", 1L << 62);
        set(children(expanded).get(0), "catalyst", true);
        assertInvalid(List.of(expanded));
    }

    @Test
    void partialChanceRemainderWithProductionMultiplierAboveOneIsDepletedSafely() throws Exception {
        Object producer = tree("partial-producer", 1, chanceStack("producer-input", 1, 0.5F));
        remainder(producer, "partial-bucket", 2);
        Object consumer = treeWithOutput("partial-consumer", chanceStack("partial-consumer", 1, 0.5F),
                runtime.stack("partial-bucket", 1));
        set(consumer, "batches", 2L);
        Object total = result(calculate(List.of(producer, consumer), null), "getTotal");
        Map<?, ?> chanceCosts = (Map<?, ?>) get(total, "chanceCosts");
        assertEquals(3L, effective(chanceCosts.get(runtime.stack("partial-bucket", 1))));
        assertEquals(0, ((Map<?, ?>) get(total, "chanceRemainders")).size());
    }

    @Test
    void idealBatchUsesNativeStartingAmountAndRejectsRecursiveProductOverflow() throws Exception {
        Object tree = tree("ideal", 3, runtime.stack("ideal-input", 1));
        Class<?> nodeType = runtime.type("dev.emi.emi.bom.MaterialNode");
        Class<?> costType = runtime.type("dev.emi.emi.bom.TreeCost");
        assertEquals(3L, call(amounts, null, "idealBatch", new Class<?>[]{nodeType, costType},
                get(tree, "goal"), get(tree, "cost")));
        Object child = children(tree).get(0);
        Object childRecipe = runtime.recipe("test:ideal-child", runtime.stack("ideal-child", 2),
                List.of(runtime.stack("ideal-nested", 2)));
        call(nodeType, child, "defineRecipe", new Class<?>[]{runtime.type("dev.emi.emi.api.recipe.EmiRecipe")}, childRecipe);
        set(child, "amount", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> call(amounts, null, "idealBatch", new Class<?>[]{nodeType, costType},
                get(tree, "goal"), get(tree, "cost")));
    }

    @Test
    void secondaryOutputProductRejectsOverflow() throws Exception {
        Object output = runtime.stack("primary", 1);
        Object secondary = runtime.stack("secondary", 2);
        Class<?> recipeType = runtime.type("dev.emi.emi.api.recipe.EmiRecipe");
        Object recipe = Proxy.newProxyInstance(recipeType.getClassLoader(), new Class<?>[]{recipeType},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getOutputs" -> List.of(output, secondary);
                    case "getInputs" -> List.of();
                    case "getId" -> null;
                    case "supportsRecipeTree" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Object tree = newTree(recipe);
        set(tree, "batches", Long.MAX_VALUE);
        assertInvalid(List.of(tree));
    }

    @Test
    void failedCalculationPreservesPublishedMapsAndEveryNodeProgress() throws Exception {
        Object valid = tree("transaction", 1, runtime.stack("input", 1));
        Object costs = calculate(List.of(valid), inventory(runtime.stack("input", 1)));
        Object goal = get(valid, "goal");
        Object child = children(valid).get(0);
        Object priorProgress = get(child, "progress");
        Object priorNeeded = get(goal, "totalNeeded");
        Object priorCost = first(result(costs, "getTotal"), "costs");
        Object invalid = leaf("invalid", 2);
        set(invalid, "batches", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> recalculate(costs, List.of(valid, invalid), null));
        assertSame(priorCost, first(result(costs, "getTotal"), "costs"));
        assertEquals(priorProgress, get(child, "progress"));
        assertEquals(priorNeeded, get(goal, "totalNeeded"));
    }

    @Test
    void candidateValidationDoesNotChangeBatchesOrProgress() throws Exception {
        Object tree = tree("candidate", 1, runtime.stack("input", 2));
        Object goal = get(tree, "goal");
        Object prior = get(goal, "progress");
        assertThrows(ArithmeticException.class, () -> call(costsType, null, "validateAmounts",
                new Class<?>[]{List.class, Map.class}, List.of(tree), Map.of(tree, Long.MAX_VALUE)));
        assertEquals(1L, get(tree, "batches"));
        assertEquals(prior, get(goal, "progress"));
    }

    @Test
    void sharedRecipeQuantityIsCheckedEvenWhenInputsAreCatalysts() throws Exception {
        Object recipe = runtime.recipe("test:shared-catalyst", runtime.stack("catalyst-product", 1),
                List.of(runtime.stack("catalyst", 1)));
        Object first = newTree(recipe);
        Object second = newTree(recipe);
        set(children(first).get(0), "catalyst", true);
        set(children(second).get(0), "catalyst", true);
        set(first, "batches", Long.MAX_VALUE - 1);
        calculate(List.of(first, second), null);
        set(second, "batches", 2L);
        assertInvalid(List.of(first, second));
    }

    @Test
    void candidateChecksInventoryDependentOverflowWithoutPublishingProgress() throws Exception {
        Object producer = tree("inventory-producer", 1, runtime.stack("producer-input", 1));
        remainder(producer, "common-consumed", 1);
        set(producer, "batches", Long.MAX_VALUE);
        Object consumer = tree("inventory-consumer", 1, runtime.stack("common-consumed", 1));
        set(consumer, "batches", Long.MAX_VALUE);
        Object extra = tree("inventory-extra", 1, runtime.stack("common-consumed", 1));
        List<?> roots = List.of(producer, consumer, extra);
        call(costsType, null, "validateAmounts", new Class<?>[]{List.class, Map.class}, roots, Map.of());
        Object goal = get(producer, "goal");
        Object previous = get(goal, "progress");
        assertThrows(ArithmeticException.class, () -> call(costsType, null, "validateAmounts",
                new Class<?>[]{List.class, Map.class, runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory")},
                roots, Map.of(), inventory(runtime.stack("inventory-producer", Long.MAX_VALUE))));
        assertEquals(previous, get(goal, "progress"));
    }

    private static long adjust(long current, long delta, boolean shift, boolean ctrl) throws Exception {
        return (long) call(amounts, null, "adjustBatch",
                new Class<?>[]{long.class, long.class, boolean.class, boolean.class}, current, delta, shift, ctrl);
    }

    private static long ceil(long amount, long divisor) throws Exception {
        return (long) call(amounts, null, "ceilDiv", new Class<?>[]{long.class, long.class}, amount, divisor);
    }

    private static Object chance(float output, float input) throws Exception {
        Class<?> chanceType = runtime.type("io.github.mango_clark.emirecipeforest.forest.ForestAmounts$Chance");
        Object chance = chanceType.getField("DEFAULT").get(null);
        chance = call(chanceType, chance, "produce", new Class<?>[]{float.class}, output);
        return call(chanceType, chance, "consume", new Class<?>[]{float.class}, input);
    }

    private static long displayExpected(long amount, Object chance) throws Exception {
        return (long) call(amounts, null, "roundExpected", new Class<?>[]{long.class, chance.getClass()}, amount, chance);
    }

    private static Object tree(String key, long output, Object... inputs) throws Exception {
        return treeWithOutput(key, runtime.stack(key, output), inputs);
    }

    private static Object treeWithOutput(String key, Object output, Object... inputs) throws Exception {
        return newTree(runtime.recipe("test:" + key, output, List.of(inputs)));
    }

    private static Object newTree(Object recipe) throws Exception {
        return runtime.type("dev.emi.emi.bom.MaterialTree")
                .getConstructor(runtime.type("dev.emi.emi.api.recipe.EmiRecipe")).newInstance(recipe);
    }

    private static Object leaf(String key, long amount) throws Exception {
        Object tree = tree("unused-" + key, 1);
        Object node = runtime.type("dev.emi.emi.bom.MaterialNode")
                .getConstructor(runtime.type("dev.emi.emi.api.stack.EmiIngredient")).newInstance(runtime.stack(key, amount));
        set(tree, "goal", node);
        return tree;
    }

    private static Object chanceStack(String key, long amount, float chance) throws Exception {
        Object stack = runtime.stack(key, amount);
        call(stack.getClass(), stack, "setChance", new Class<?>[]{float.class}, chance);
        return stack;
    }

    private static List<?> children(Object tree) throws Exception {
        return (List<?>) get(get(tree, "goal"), "children");
    }

    private static void remainder(Object tree, String key, long amount) throws Exception {
        Object child = children(tree).get(0);
        set(child, "remainder", runtime.stack(key, 1));
        set(child, "remainderAmount", amount);
    }

    private static Object inventory(Object... stacks) throws Exception {
        return runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory").getConstructor(List.class).newInstance(List.of(stacks));
    }

    private static Object calculate(List<?> trees, Object inventory) throws Exception {
        return call(costsType, null, "calculateNew", new Class<?>[]{List.class,
                runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory")}, trees, inventory);
    }

    private static void recalculate(Object costs, List<?> trees, Object inventory) throws Exception {
        call(costsType, costs, "calculate", new Class<?>[]{List.class,
                runtime.type("dev.emi.emi.api.recipe.EmiPlayerInventory")}, trees, inventory);
    }

    private static void assertInvalid(List<?> trees) {
        assertThrows(ArithmeticException.class, () -> calculate(trees, null));
    }

    private static Object result(Object costs, String name) throws Exception {
        return call(costsType, costs, name, new Class<?>[0]);
    }

    private static Object first(Object cost, String map) throws Exception {
        return ((Map<?, ?>) get(cost, map)).values().iterator().next();
    }

    private static long amount(Object cost) throws Exception {
        return (long) get(cost, "amount");
    }

    private static long effective(Object cost) throws Exception {
        return (long) call(cost.getClass(), cost, "getEffectiveAmount", new Class<?>[0]);
    }

    private static Object get(Object owner, String field) throws Exception {
        return owner.getClass().getField(field).get(owner);
    }

    private static void set(Object owner, String field, Object value) throws Exception {
        owner.getClass().getField(field).set(owner, value);
    }

    private static Object call(Class<?> type, Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = type.getMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(owner, args);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
