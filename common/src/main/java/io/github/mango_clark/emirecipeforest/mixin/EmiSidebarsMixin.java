package io.github.mango_clark.emirecipeforest.mixin;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiSidebars;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiSidebars.class, remap = false)
public abstract class EmiSidebarsMixin {
    @Inject(method = "getStacks", at = @At("RETURN"), cancellable = true)
    private static void recipeForest$appendBookmarkCards(SidebarType type,
            CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
        if (type != SidebarType.FAVORITES) {
            return;
        }

        List<EmiIngredient> cards = ForestBookmarks.cards();
        if (cards.isEmpty()) {
            return;
        }

        List<EmiIngredient> combined = new ArrayList<>(cir.getReturnValue().size() + cards.size());
        combined.addAll(cir.getReturnValue());
        combined.addAll(cards);
        cir.setReturnValue(List.copyOf(combined));
    }
}
