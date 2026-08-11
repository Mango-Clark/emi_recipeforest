package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialTree;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaterialTree.class, remap = false)
public abstract class MaterialTreeMixin {
    @Inject(
            method = "addResolution(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void recipeForest$applyPendingResolution(EmiIngredient ingredient, EmiRecipe recipe, CallbackInfo ci) {
        if (ForestManager.tryApplyPendingResolution((MaterialTree) (Object) this, ingredient, recipe)) {
            ci.cancel();
        }
    }
}
