package io.github.mango_clark.emirecipeforest;

import net.fabricmc.api.ClientModInitializer;

public class EmiRecipeForest implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CommonClass.init();
    }
}
