package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.input.EmiBind;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ListWidget.Entry;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ConfigState;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import io.github.mango_clark.emirecipeforest.screen.RecipeForestTextures;
import io.github.mango_clark.emirecipeforest.screen.widget.RecipeForestConfigWidgets;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds RecipeForest settings to EMI's native configuration list. */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin extends Screen {
    @Shadow
    public ListWidget list;
    @Shadow
    private ConfigSearch search;
    @Shadow
    public EmiBind activeBind;
    @Unique
    private boolean recipeForest$groupCollapsed;
    @Unique
    private boolean recipeForest$forestBindWasActive;
    @Unique
    private boolean recipeForest$anyBindWasActive;
    @Unique
    private ConfigState recipeForest$originalConfig;

    protected ConfigScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$captureOriginalConfig(Screen last, CallbackInfo ci) {
        recipeForest$originalConfig = ForestBookmarks.captureConfigState();
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void recipeForest$rememberGroupState(CallbackInfo ci) {
        recipeForest$forestBindWasActive = false;
        recipeForest$anyBindWasActive = false;
        if (list == null) {
            return;
        }
        for (Entry entry : list.children()) {
            if (entry instanceof GroupNameWidget group && RecipeForestConfigWidgets.GROUP_ID.equals(group.id)) {
                recipeForest$groupCollapsed = group.collapsed;
                return;
            }
        }
    }

    @Inject(method = "init", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/ConfigScreen;addJumpButtons()V"))
    private void recipeForest$addSettingsGroup(CallbackInfo ci) {
        RecipeForestConfigWidgets.addSettings((ConfigScreen) (Object) this, list, search,
                recipeForest$groupCollapsed);
    }

    @ModifyVariable(method = "updateChanges", at = @At("LOAD"), ordinal = 0, require = 2)
    private int recipeForest$includeConfigChanges(int emiChanges) {
        return emiChanges + recipeForest$originalConfig.countChanges(ForestBookmarks.captureConfigState());
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/EmiPort;newButton(IIIILnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/client/gui/components/Button$OnPress;)"
                    + "Lnet/minecraft/client/gui/components/Button;", ordinal = 0), index = 5)
    private Button.OnPress recipeForest$includeConfigInRevert(Button.OnPress nativeRevert) {
        return button -> {
            ForestBookmarks.restoreConfigState(recipeForest$originalConfig);
            ForestBind.INSTANCE.reloadFromBookmarks();
            nativeRevert.onPress(button);
        };
    }

    @Redirect(method = "addJumpButtons", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/widget/config/ListWidget;getLogicalHeight()I"), require = 2)
    private int recipeForest$reserveJumpButtonSpace(ListWidget list) {
        return list.getLogicalHeight() - RecipeForestTextures.ICON_SIZE * 2;
    }

    @Inject(method = "addJumpButtons", at = @At("RETURN"))
    private void recipeForest$addJumpButtons(CallbackInfo ci) {
        for (ConfigJumpButton button : RecipeForestConfigWidgets.createJumpButtons(
                (ConfigScreen) (Object) this)) {
            addRenderableWidget(button);
        }
    }

    @Inject(method = { "keyPressed", "keyReleased" }, at = @At("HEAD"))
    private void recipeForest$trackForestBindKeyStart(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$forestBindWasActive |= activeBind == ForestBind.INSTANCE;
        recipeForest$anyBindWasActive |= activeBind != null;
    }

    @Inject(method = { "keyPressed", "keyReleased" }, at = @At("RETURN"))
    private void recipeForest$trackForestBindKeyEnd(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$persistFinishedForestBind();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void recipeForest$trackForestBindMouseStart(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$forestBindWasActive |= activeBind == ForestBind.INSTANCE;
        recipeForest$anyBindWasActive |= activeBind != null;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void recipeForest$trackForestBindMouseEnd(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$persistFinishedForestBind();
    }

    @Unique
    private void recipeForest$persistFinishedForestBind() {
        if (recipeForest$anyBindWasActive && activeBind == null) {
            recipeForest$anyBindWasActive = false;
        }
        if (recipeForest$forestBindWasActive && activeBind != ForestBind.INSTANCE) {
            recipeForest$forestBindWasActive = false;
            ForestBind.INSTANCE.setBinds(ForestBind.INSTANCE.boundKeys.stream()
                    .filter(key -> !key.isUnbound()).toArray(EmiBind.ModifiedKey[]::new));
            ((ConfigScreen) (Object) this).updateChanges();
        }
    }

}
