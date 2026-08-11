package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.EmiScreenManager;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.SearchBookmark;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.TreeBookmark;
import io.github.mango_clark.emirecipeforest.screen.BookmarkNameScreen;
import io.github.mango_clark.emirecipeforest.screen.ForestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {
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
    private static void recipeForest$clearDragState() {
        EmiScreenManager.pressedStack = EmiStack.EMPTY;
        EmiScreenManager.draggedStack = EmiStack.EMPTY;
    }
}
