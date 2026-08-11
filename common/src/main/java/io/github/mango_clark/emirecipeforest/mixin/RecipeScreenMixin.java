package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.screen.RecipeScreen;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps aggregate Forest resolution state scoped to one explicit recipe picker. */
@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$cancelStaleResolutionOnOpen(CallbackInfo ci) {
        ForestManager.cancelPendingResolution();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void recipeForest$cancelPendingResolutionOnClose(CallbackInfo ci) {
        ForestManager.cancelPendingResolution();
    }
}
