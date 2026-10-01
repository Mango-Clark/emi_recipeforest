package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.api.stack.EmiIngredient;
import io.github.mango_clark.emirecipeforest.screen.ForestSidebar.RootCard;
import io.github.mango_clark.emirecipeforest.forest.ForestFavorites;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiFavorites.class, remap = false)
public abstract class EmiFavoritesMixin {
    // EMI retains EmiFavorite instances directly. Strip the session-only root identity
    // at the storage boundary so an ordinary favorite cannot mutate the live forest.
    @ModifyVariable(method = {
            "addFavorite(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            "addFavoriteAt"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static EmiIngredient recipeForest$detachRootIdentity(EmiIngredient ingredient) {
        return ingredient instanceof RootCard root ? new EmiFavorite(root.getStack(), root.getRecipe()) : ingredient;
    }

    @Inject(method = "updateSynthetic", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$updateSynthetic(EmiPlayerInventory inventory, CallbackInfo ci) {
        if (ForestManager.size() > 1 && ForestManager.isCraftingMode()) {
            ForestFavorites.updateSynthetic(inventory);
            ci.cancel();
        }
    }
}
