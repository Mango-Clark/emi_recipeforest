package io.github.mango_clark.emirecipeforest.mixin;

import java.util.LinkedHashSet;
import java.util.List;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.SearchBookmark;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.TreeBookmark;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import io.github.mango_clark.emirecipeforest.screen.BookmarkNameScreen;
import io.github.mango_clark.emirecipeforest.screen.ForestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {
    @Shadow
    private static EmiPlayerInventory lastPlayerInventory;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void recipeForest$replaceTreeButtonCallback(CallbackInfo ci) {
        EmiScreenManager.tree = new SizedButtonWidget(0, 0, 20, 20, 184, 0, () -> true, button -> {
            if (ForestManager.isEmpty()) {
                EmiApi.viewRecipeTree();
            } else {
                ForestScreen.open();
            }
        }, List.of(Component.translatable("tooltip.emi.recipe_tree")));
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$addHoveredRecipeToForest(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ForestBind.INSTANCE.matchesKey(keyCode, scanCode) || EmiApi.getHandledScreen() == null
                || recipeForest$hasFocusedTextField()) {
            return;
        }

        if (recipeForest$addHoveredRecipe(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$addHoveredRecipeToForest(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ForestBind.INSTANCE.matchesMouse(button) || EmiApi.getHandledScreen() == null
                || recipeForest$hasFocusedTextField()) {
            return;
        }
        if (recipeForest$addHoveredRecipe((int) mouseX, (int) mouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean recipeForest$addHoveredRecipe(int mouseX, int mouseY) {
        EmiStackInteraction hovered = EmiScreenManager.getHoveredStack(
                mouseX, mouseY, true);
        if (hovered == null || hovered.isEmpty()) {
            return false;
        }
        EmiRecipe recipe = hovered.getRecipeContext();
        if ((recipe == null || !recipe.supportsRecipeTree()) && lastPlayerInventory != null) {
            LinkedHashSet<EmiRecipe> candidates = new LinkedHashSet<>();
            for (EmiStack stack : hovered.getStack().getEmiStacks()) {
                candidates.addAll(EmiApi.getRecipeManager().getRecipesByOutput(stack));
            }
            recipe = EmiUtil.getPreferredRecipe(List.copyOf(candidates), lastPlayerInventory, false);
        }
        if (recipe == null || !recipe.supportsRecipeTree()) {
            return false;
        }

        ForestManager.add(recipe);
        ForestScreen.open();
        return true;
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$handleBookmarkCard(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        EmiIngredient card = EmiScreenManager.pressedStack;
        if (!recipeForest$isBookmarkCard(card)) {
            if (recipeForest$isBookmarkCard(EmiScreenManager.draggedStack)) {
                recipeForest$clearDragState();
                cir.setReturnValue(true);
            }
            return;
        }

        try {
            if (button == 1) {
                if (card instanceof TreeBookmark tree && EmiInput.isShiftDown()) {
                    BookmarkNameScreen.openForRename(tree);
                } else if (ForestBookmarks.remove(card)) {
                    EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
                }
            } else if (button == 0) {
                boolean applied = ForestBookmarks.apply(card);
                if (card instanceof TreeBookmark) {
                    if (applied) {
                        ForestScreen.open();
                    } else if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.translatable("message.emi_recipeforest.bookmark.invalid"), false);
                    }
                }
            }
        } finally {
            recipeForest$clearDragState();
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$cancelBookmarkDrag(double mouseX, double mouseY, int button,
            double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (recipeForest$isBookmarkCard(EmiScreenManager.pressedStack)
                || recipeForest$isBookmarkCard(EmiScreenManager.draggedStack)) {
            recipeForest$clearDragState();
            cir.setReturnValue(false);
        }
    }

    @Unique
    private static boolean recipeForest$isBookmarkCard(EmiIngredient ingredient) {
        return ingredient instanceof SearchBookmark || ingredient instanceof TreeBookmark;
    }

    @Unique
    private static boolean recipeForest$hasFocusedTextField() {
        if (EmiScreenManager.search != null && EmiScreenManager.search.canConsumeInput()) {
            return true;
        }
        return Minecraft.getInstance().screen instanceof ContainerEventHandler handler
                && recipeForest$hasFocusedTextField(handler, 10);
    }

    @Unique
    private static boolean recipeForest$hasFocusedTextField(ContainerEventHandler parent, int depthRemaining) {
        if (depthRemaining <= 0) {
            return false;
        }
        for (GuiEventListener child : parent.children()) {
            if (child instanceof EditBox field && field.visible && field.canConsumeInput()) {
                return true;
            }
            if (child instanceof ContainerEventHandler nested
                    && recipeForest$hasFocusedTextField(nested, depthRemaining - 1)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void recipeForest$clearDragState() {
        EmiScreenManager.pressedStack = EmiStack.EMPTY;
        EmiScreenManager.draggedStack = EmiStack.EMPTY;
    }
}
