package io.github.mango_clark.emirecipeforest.mixin;

import java.util.List;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeTreeButtonWidget.class, remap = false)
public abstract class RecipeTreeButtonWidgetMixin extends RecipeButtonWidget {
    @Unique
    private static final ResourceLocation RECIPE_FOREST$BUTTONS = EmiPort.id("emi_recipeforest",
            "textures/gui/buttons.png");

    @Unique
    private EmiRecipe recipeForest$recipe;

    public RecipeTreeButtonWidgetMixin(int x, int y, int u, int v, EmiRecipe recipe) {
        super(x, y, u, v, recipe);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$captureRecipe(int x, int y, EmiRecipe recipe, CallbackInfo ci) {
        recipeForest$recipe = recipe;
    }

    @Inject(method = "getTooltip(II)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void recipeForest$tooltip(int mouseX, int mouseY,
            CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        if (!EmiInput.isShiftDown()) {
            cir.setReturnValue(List.of(ClientTooltipComponent.create(
                    Component.translatable("tooltip.emi_recipeforest.add_to_forest").getVisualOrderText())));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (EmiInput.isShiftDown()) {
            super.render(graphics, mouseX, mouseY, delta);
            return;
        }
        EmiDrawContext context = EmiDrawContext.wrap(graphics);
        context.resetColor();
        int textureOffset = ForestManager.containsRecipe(recipeForest$recipe) ? 24 : 0;
        textureOffset += getBounds().contains(mouseX, mouseY) ? 12 : 0;
        context.drawTexture(RECIPE_FOREST$BUTTONS, x, y, 36, textureOffset, 12, 12);
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
        ForestManager.add(recipeForest$recipe);
        cir.setReturnValue(true);
    }
}
