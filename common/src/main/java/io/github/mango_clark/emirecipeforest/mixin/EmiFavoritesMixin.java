package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.runtime.EmiFavorites;
import io.github.mango_clark.emirecipeforest.forest.ForestFavorites;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiFavorites.class, remap = false)
public abstract class EmiFavoritesMixin {
    @Inject(method = "updateSynthetic", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$updateSynthetic(EmiPlayerInventory inventory, CallbackInfo ci) {
        if (ForestManager.size() > 1 && ForestManager.isCraftingMode()) {
            ForestFavorites.updateSynthetic(inventory);
            ci.cancel();
        }
    }
}
