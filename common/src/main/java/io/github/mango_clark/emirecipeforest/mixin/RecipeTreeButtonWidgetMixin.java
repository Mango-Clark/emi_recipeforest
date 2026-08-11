package io.github.mango_clark.emirecipeforest.mixin;

import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import io.github.mango_clark.emirecipeforest.screen.ForestScreen;
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
    private static final ResourceLocation RECIPE_FOREST$TEXTURE = new ResourceLocation(
            "emi_recipeforest", "textures/gui/recipe_tree_buttons.png");
    @Unique
    private EmiRecipe recipeForest$recipe;
    @Unique
    private int recipeForest$x;
    @Unique
    private int recipeForest$y;

    public RecipeTreeButtonWidgetMixin(int x, int y, int u, int v, EmiRecipe recipe) {
        super(x, y, u, v, recipe);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$captureRecipe(int x, int y, EmiRecipe recipe, CallbackInfo ci) {
        recipeForest$recipe = recipe;
        recipeForest$x = x;
        recipeForest$y = y;
    }

    @Inject(method = "getTooltip(II)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void recipeForest$tooltip(int mouseX, int mouseY,
            CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
        String key = EmiInput.isShiftDown()
                ? "tooltip.emi_recipeforest.view_solo_tree"
                : "tooltip.emi_recipeforest.add_to_forest";
        cir.setReturnValue(List.of(ClientTooltipComponent.create(Component.translatable(key).getVisualOrderText())));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = getBounds().contains(mouseX, mouseY);
        int u = EmiInput.isShiftDown() ? 24 : 0;
        if (hovered) {
            u += 12;
        }
        graphics.blit(RECIPE_FOREST$TEXTURE, recipeForest$x, recipeForest$y,
                (float) u, 0, 12, 12, 48, 12);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void recipeForest$handleClick(int mouseX, int mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            cir.setReturnValue(true);
            return;
        }

        ((RecipeTreeButtonWidget) (Object) this).playButtonSound();
        if (EmiInput.isShiftDown()) {
            ForestManager.replaceSolo(recipeForest$recipe);
            EmiApi.viewRecipeTree();
        } else {
            ForestManager.add(recipeForest$recipe);
            ForestScreen.open();
        }
        cir.setReturnValue(true);
    }
}
