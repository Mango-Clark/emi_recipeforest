package io.github.mango_clark.emirecipeforest.input;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.EmiConfig.ConfigValue;
import dev.emi.emi.input.EmiBind;
import io.github.mango_clark.emirecipeforest.Constants;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.BindingType;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ForestBinding;
import net.minecraft.network.chat.Component;

/** Addon-owned native EMI bind; it is deliberately not registered in {@link EmiConfig}. */
public final class ForestBind extends EmiBind {
    public static final String TRANSLATION_KEY = "key.emi_recipeforest.add_to_forest";
    public static final ForestBind INSTANCE = new ForestBind();

    private boolean initialized;

    private ForestBind() {
        super(TRANSLATION_KEY, fromBookmarks());
        initialized = true;
    }

    public void reloadFromBookmarks() {
        initialized = false;
        super.setBinds(fromBookmarks());
        initialized = true;
    }

    @Override
    public void setBind(int offset, ModifiedKey key) {
        super.setBind(offset, key);
        persist();
    }

    @Override
    public void setBinds(ModifiedKey... keys) {
        super.setBinds(normalize(keys));
        persist();
    }

    @Override
    public void setToDefault() {
        ForestBookmarks.resetForestBindings();
        reloadFromBookmarks();
    }

    public List<Collision> getCollisions() {
        LinkedHashSet<Collision> collisions = new LinkedHashSet<>();
        for (Field field : EmiConfig.class.getFields()) {
            ConfigValue config = field.getAnnotation(ConfigValue.class);
            if (config == null || !config.value().startsWith("binds.")
                    || field.getType() != EmiBind.class || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                EmiBind other = (EmiBind) field.get(null);
                if (other != null && collides(other)) {
                    collisions.add(new Collision(config.value(), other.translationKey,
                            Component.translatable(other.translationKey)));
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Constants.LOG.warn("Could not inspect EMI bind '{}' for a RecipeForest collision", field.getName(),
                        exception);
            }
        }
        return List.copyOf(collisions);
    }

    private boolean collides(EmiBind other) {
        for (ModifiedKey ours : boundKeys) {
            if (ours.isUnbound()) {
                continue;
            }
            for (ModifiedKey theirs : other.boundKeys) {
                if (!theirs.isUnbound() && sameInput(ours, theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sameInput(ModifiedKey first, ModifiedKey second) {
        return first.key().getType() == second.key().getType()
                && first.key().getValue() == second.key().getValue()
                && first.modifiersToMatch() == second.modifiersToMatch();
    }

    private void persist() {
        if (!initialized) {
            return;
        }
        List<ForestBinding> serialized = new ArrayList<>(ForestBookmarks.MAX_FOREST_BINDINGS);
        for (ModifiedKey binding : boundKeys) {
            if (!binding.isUnbound() && serialized.size() < ForestBookmarks.MAX_FOREST_BINDINGS) {
                BindingType type = switch (binding.key().getType()) {
                    case KEYSYM -> BindingType.KEYSYM;
                    case SCANCODE -> BindingType.SCANCODE;
                    case MOUSE -> BindingType.MOUSE;
                };
                ForestBinding value = new ForestBinding(type, binding.key().getName(), binding.key().getValue(),
                        binding.modifiers());
                if (!serialized.contains(value)) {
                    serialized.add(value);
                }
            }
        }
        ForestBookmarks.setForestBindings(serialized);
    }

    private static ModifiedKey[] fromBookmarks() {
        return ForestBookmarks.getForestBindings().stream().map(ForestBind::toModifiedKey)
                .toArray(ModifiedKey[]::new);
    }

    private static ModifiedKey toModifiedKey(ForestBinding binding) {
        InputConstants.Type type = switch (binding.type()) {
            case KEYSYM -> InputConstants.Type.KEYSYM;
            case SCANCODE -> InputConstants.Type.SCANCODE;
            case MOUSE -> InputConstants.Type.MOUSE;
        };
        return new ModifiedKey(type.getOrCreate(binding.value()), binding.modifiers());
    }

    private static ModifiedKey[] normalize(ModifiedKey[] keys) {
        if (keys == null) {
            return new ModifiedKey[0];
        }
        LinkedHashSet<ModifiedKey> normalized = new LinkedHashSet<>();
        for (ModifiedKey key : keys) {
            if (key != null && !key.isUnbound() && normalized.size() < ForestBookmarks.MAX_FOREST_BINDINGS) {
                normalized.add(key);
            }
        }
        return normalized.toArray(ModifiedKey[]::new);
    }

    public record Collision(String configKey, String translationKey, Component translatedName) {
    }
}
