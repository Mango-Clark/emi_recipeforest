package io.github.mango_clark.emirecipeforest.mixin;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.screen.BoMScreen;
import io.github.mango_clark.emirecipeforest.screen.ForestScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EmiApi.class, remap = false)
public abstract class EmiApiMixin {
	@Redirect(
		method = "viewRecipeTree",
		at = @At(value = "NEW", target = "dev/emi/emi/screen/BoMScreen"),
		require = 2
	)
	private static BoMScreen recipeForest$createTreeScreen(AbstractContainerScreen<?> old) {
		return ForestScreen.isForestOpenRequested() ? new ForestScreen(old) : new BoMScreen(old);
	}
}
