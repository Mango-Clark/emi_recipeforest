package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.EmiApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = EmiApi.class, remap = false)
public interface EmiApiMixin {
    /** Invokes EMI's native screen-history transition before opening a RecipeForest screen. */
    @Invoker("push")
    static void recipeForest$pushHistory() {
        throw new AssertionError();
    }
}
