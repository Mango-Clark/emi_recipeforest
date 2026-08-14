package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.EmiPort;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks overridden EMI tree bindings when they collide with the Forest binding. */
@Mixin(value = ConfigEntryWidget.class, remap = false)
public abstract class ConfigEntryWidgetMixin {
    @Unique
    private static final ResourceLocation RECIPE_FOREST$WIDGETS = EmiPort.id("emi_recipeforest",
            "textures/gui/widgets.png");

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
        boolean collision = ForestBind.INSTANCE.getCollisions().stream()
                .anyMatch(found -> found.translationKey().equals(bind.translationKey));
        if (!collision) {
            return;
        }
        EmiDrawContext context = EmiDrawContext.wrap(raw);
        context.setColor(1, 0.65f, 0.2f);
        context.drawTexture(RECIPE_FOREST$WIDGETS, x + width - 244, y + 2, 0, 0, 16, 16, 16, 64, 32);
        context.resetColor();
    }
}
