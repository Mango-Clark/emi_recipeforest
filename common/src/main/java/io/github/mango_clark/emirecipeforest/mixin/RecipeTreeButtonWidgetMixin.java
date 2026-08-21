package io.github.mango_clark.emirecipeforest.mixin;

import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.tooltip.EmiTooltip;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeTreeButtonWidget.class, remap = false)
public abstract class RecipeTreeButtonWidgetMixin extends RecipeButtonWidget {
    public RecipeTreeButtonWidgetMixin(int x, int y, int u, int v, EmiRecipe recipe) {
        super(x, y, u, v, recipe);
    }

    @Inject(method = "getTextureOffset", at = @At("HEAD"), cancellable = true)
    private void recipeForest$textureOffset(int mouseX, int mouseY, CallbackInfoReturnable<Integer> cir) {
        if (!EmiInput.isShiftDown()) {
            int offset = super.getTextureOffset(mouseX, mouseY);
            if (ForestManager.containsRecipe(recipe)) {
                offset += 36;
            }
            cir.setReturnValue(offset);
        }
    }

    @Inject(method = "getTooltip(II)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void recipeForest$tooltip(int mouseX, int mouseY,
            CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        if (!EmiInput.isShiftDown()) {
            cir.setReturnValue(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.add_to_forest"));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void recipeForest$handleClick(int mouseX, int mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (EmiInput.isShiftDown()) {
            return;
        }
        if (button != 0) {
            cir.setReturnValue(true);
            return;
        }

        ((RecipeTreeButtonWidget) (Object) this).playButtonSound();
        ForestManager.add(recipe);
        cir.setReturnValue(true);
    }
}
