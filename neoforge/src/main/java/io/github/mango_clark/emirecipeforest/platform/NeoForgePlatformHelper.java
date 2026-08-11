package io.github.mango_clark.emirecipeforest.platform;

import io.github.mango_clark.emirecipeforest.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

import java.util.Optional;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.isLoaded(modId);
        }
        LoadingModList loadingModList = LoadingModList.get();
        return loadingModList != null
                && loadingModList.getMods().stream().anyMatch(info -> info.getModId().equals(modId));
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString());
        }
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList == null) {
            return Optional.empty();
        }
        return loadingModList.getMods().stream()
                .filter(info -> info.getModId().equals(modId))
                .map(info -> info.getVersion().toString())
                .findFirst();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
