package io.github.mango_clark.emirecipeforest.screen;

import dev.emi.emi.EmiPort;
import io.github.mango_clark.emirecipeforest.Constants;
import net.minecraft.resources.ResourceLocation;

/** Shared GUI textures and sprite coordinates used by RecipeForest screens and mixins. */
public final class RecipeForestTextures {
    /** 256x256 sprite sheet; EmiDrawContext's short drawTexture overload matches this size. */
    public static final ResourceLocation WIDGETS = EmiPort.id(Constants.MOD_ID, "textures/gui/widgets.png");

    public static final int ICON_SIZE = 16;
    public static final int FOREST_ICON_U = 0;
    public static final int FOREST_ICON_V = 16;
    public static final int DETAILS_ICON_U = 16;
    public static final int DETAILS_ICON_V = 16;

    private RecipeForestTextures() {
    }
}
