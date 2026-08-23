package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.widget.RecipeButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps EMI's native recipe-button renderer while selecting RecipeForest's tree-button texture. */
@Mixin(value = RecipeButtonWidget.class, remap = false)
public abstract class RecipeButtonWidgetMixin {
    @Unique
    private static final ResourceLocation RECIPE_FOREST$BUTTONS = EmiPort.id("emi_recipeforest",
            "textures/gui/buttons.png");

    @Redirect(method = "render", at = @At(value = "FIELD",
            target = "Ldev/emi/emi/EmiRenderHelper;BUTTONS:Lnet/minecraft/resources/ResourceLocation;",
            opcode = Opcodes.GETSTATIC))
    private ResourceLocation recipeForest$treeButtonTexture() {
        return (Object) this instanceof RecipeTreeButtonWidget && !EmiInput.isShiftDown()
                ? RECIPE_FOREST$BUTTONS
                : EmiRenderHelper.BUTTONS;
    }
}
