package io.github.mango_clark.emirecipeforest;

import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class EmiRecipeForest {

    public EmiRecipeForest() {
        Constants.LOG.info("Hello Forge world!");
        CommonClass.init();
    }
}
