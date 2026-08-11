package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.runtime.EmiReloadManager;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiReloadManager.class, remap = false)
public abstract class EmiReloadManagerMixin {
    @Inject(method = {"clear", "reload"}, at = @At("HEAD"))
    private static void recipeForest$clearLiveForest(CallbackInfo ci) {
        ForestManager.clear();
    }
}
