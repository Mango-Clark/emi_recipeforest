package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BoM.class, remap = false)
public abstract class BoMMixin {
    @Inject(method = "setGoal", at = @At("RETURN"))
    private static void recipeForest$synchronizeExternalGoal(EmiRecipe recipe, CallbackInfo ci) {
        if (!ForestManager.isSynchronizingGoal()) {
            ForestManager.synchronizeSetGoal();
        }
    }
}
