package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.runtime.EmiPersistentData;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiPersistentData.class, remap = false)
public abstract class EmiPersistentDataMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private static void recipeForest$loadBookmarks(CallbackInfo ci) {
        ForestBookmarks.load();
        ForestBind.INSTANCE.reloadFromBookmarks();
    }
}
