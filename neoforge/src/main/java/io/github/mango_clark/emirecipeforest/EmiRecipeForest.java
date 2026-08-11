package io.github.mango_clark.emirecipeforest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class EmiRecipeForest {

    public EmiRecipeForest() {
        CommonClass.init();
    }
}
