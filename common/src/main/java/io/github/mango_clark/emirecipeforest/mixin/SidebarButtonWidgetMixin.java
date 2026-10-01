package io.github.mango_clark.emirecipeforest.mixin;

import java.util.List;

import dev.emi.emi.EmiPort;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import dev.emi.emi.screen.widget.SidebarButtonWidget;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import io.github.mango_clark.emirecipeforest.screen.ForestSidebar;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only the closed SidebarType enum's header presentation for addon panels. */
@Mixin(value = SidebarButtonWidget.class, remap = false)
public abstract class SidebarButtonWidgetMixin extends SizedButtonWidget {
    @Shadow @Final private SidebarPanel panel;

    protected SidebarButtonWidgetMixin(int x, int y, Button.OnPress action) {
        super(x, y, 16, 16, 0, 0, () -> true, action);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void recipeForest$header(int x, int y, int width, int height, SidebarPanel panel, CallbackInfo ci) {
        if (panel instanceof ForestSidebar forest) {
            texture = EmiPort.id("emi_recipeforest", "textures/gui/widgets.png");
            text = () -> List.of(Component.translatable(forest.isRoots()
                    ? "sidebar.emi_recipeforest.roots" : "sidebar.emi_recipeforest.saved"));
        }
    }

    @Inject(method = "getU", at = @At("HEAD"), cancellable = true)
    private void recipeForest$iconU(int mouseX, int mouseY, CallbackInfoReturnable<Integer> cir) {
        if (panel instanceof ForestSidebar forest) {
            cir.setReturnValue(forest.isRoots() ? 240 : 224);
        }
    }

    @Inject(method = "getV", at = @At("HEAD"), cancellable = true)
    private void recipeForest$iconV(int mouseX, int mouseY, CallbackInfoReturnable<Integer> cir) {
        if (panel instanceof ForestSidebar) {
            cir.setReturnValue(0);
        }
    }
}
