package io.github.mango_clark.emirecipeforest.mixin;

import java.util.function.Function;
import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps aggregate Forest resolution state scoped to one explicit recipe picker. */
@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin {
    @Shadow
    private List<WidgetGroup> currentPage;

    @Unique
    private boolean recipeForest$forestKeyPressed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$cancelStaleResolutionOnOpen(CallbackInfo ci) {
        ForestManager.cancelPendingResolution();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void recipeForest$cancelPendingResolutionOnClose(CallbackInfo ci) {
        ForestManager.cancelPendingResolution();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void recipeForest$captureForestKey(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$forestKeyPressed = ForestBind.INSTANCE.matchesKey(keyCode, scanCode)
                && (EmiScreenManager.search == null || !EmiScreenManager.search.canConsumeInput());
        if (!recipeForest$forestKeyPressed) {
            return;
        }
        for (WidgetGroup group : currentPage) {
            if (group.recipe != null && group.recipe.supportsRecipeTree()
                    && EmiScreenManager.lastMouseX >= group.x()
                    && EmiScreenManager.lastMouseX < group.x() + group.getWidth()
                    && EmiScreenManager.lastMouseY >= group.y()
                    && EmiScreenManager.lastMouseY < group.y() + group.getHeight()) {
                ForestManager.add(group.recipe);
                recipeForest$forestKeyPressed = false;
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Redirect(method = "keyPressed", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/EmiScreenManager;recipeInteraction("
                    + "Ldev/emi/emi/api/recipe/EmiRecipe;Ljava/util/function/Function;)Z"))
    private boolean recipeForest$routeForestKey(EmiRecipe recipe, Function<EmiBind, Boolean> input) {
        if (recipeForest$forestKeyPressed && recipe != null && recipe.supportsRecipeTree()) {
            ForestManager.add(recipe);
            return true;
        }
        return EmiScreenManager.recipeInteraction(recipe, input);
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void recipeForest$clearForestKey(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$forestKeyPressed = false;
    }
}
