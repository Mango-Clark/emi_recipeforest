package io.github.mango_clark.emirecipeforest.mixin;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.EmiSearchWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiSearchWidget.class, remap = false)
public abstract class EmiSearchWidgetMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void recipeForest$saveSearchBookmark(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        EmiSearchWidget widget = (EmiSearchWidget) (Object) this;
        if (!widget.isFocused() || !EmiInput.isControlDown() || keyCode != GLFW.GLFW_KEY_ENTER) {
            return;
        }

        String query = widget.getValue().trim();
        if (query.isEmpty()) {
            return;
        }

        ForestBookmarks.addSearch(query);
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        cir.setReturnValue(true);
    }
}
