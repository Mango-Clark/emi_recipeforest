package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import io.github.mango_clark.emirecipeforest.screen.RecipeForestTextures;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks EMI tree bindings whose actions are replaced by RecipeForest. */
@Mixin(value = ConfigEntryWidget.class, remap = false)
public abstract class ConfigEntryWidgetMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void recipeForest$renderOverrideMarker(GuiGraphics raw, int index, int y, int x, int width, int height,
            int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof EmiBindWidget widget)) {
            return;
        }
        EmiBind bind = ((EmiBindWidgetAccessor) widget).recipeForest$getBind();
        if (bind != EmiConfig.viewTree && bind != EmiConfig.viewStackTree) {
            return;
        }
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        context.setColor(1, 0.65f, 0.2f);
        context.drawTexture(RecipeForestTextures.WIDGETS, x + width - 244, y + 2,
                RecipeForestTextures.FOREST_ICON_U, RecipeForestTextures.FOREST_ICON_V,
                RecipeForestTextures.ICON_SIZE, RecipeForestTextures.ICON_SIZE);
        context.resetColor();
    }
}
