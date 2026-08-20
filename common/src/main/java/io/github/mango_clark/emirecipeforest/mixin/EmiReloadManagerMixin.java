package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.runtime.EmiReloadManager;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiReloadManager.class, remap = false)
public abstract class EmiReloadManagerMixin {
    @Inject(method = "clear", at = @At("HEAD"))
    private static void recipeForest$clearSessionForest(CallbackInfo ci) {
        recipeForest$runOnClientThreadAndWait(ForestManager::clear);
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private static void recipeForest$captureLiveForest(CallbackInfo ci) {
        recipeForest$runOnClientThreadAndWait(ForestManager::beginRecipeReload);
    }

    @Inject(method = "isLoaded", at = @At("RETURN"))
    private static void recipeForest$restoreReloadedForest(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && ForestManager.hasPendingRecipeReload()) {
            recipeForest$runOnClientThreadAndWait(ForestManager::finishRecipeReload);
        }
    }

    @Unique
    private static void recipeForest$runOnClientThreadAndWait(Runnable action) {
        Minecraft client = Minecraft.getInstance();
        if (client.isSameThread()) {
            action.run();
        } else {
            client.submit(action).join();
        }
    }
}
