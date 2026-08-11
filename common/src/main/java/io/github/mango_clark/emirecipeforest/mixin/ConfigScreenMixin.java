package io.github.mango_clark.emirecipeforest.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.InputConstants;

import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ListWidget.Entry;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.RootLayout;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds RecipeForest settings to EMI's native configuration list. */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin extends Screen {
    @Unique
    private static final String RECIPE_FOREST$GROUP_ID = "recipeforest";
    @Unique
    private static final int RECIPE_FOREST$CONTROL_WIDTH = 150;
    @Unique
    private static final int RECIPE_FOREST$BUTTON_HEIGHT = 20;

    @Shadow
    public ListWidget list;
    @Shadow
    private ConfigSearch search;

    @Unique
    private boolean recipeForest$groupCollapsed;
    @Unique
    private boolean recipeForest$capturingKey;
    @Unique
    private boolean recipeForest$consumeNextKeyRelease;
    @Unique
    private boolean recipeForest$keyConflict;

    protected ConfigScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void recipeForest$rememberGroupState(CallbackInfo ci) {
        recipeForest$capturingKey = false;
        recipeForest$consumeNextKeyRelease = false;
        recipeForest$keyConflict = false;
        if (ForestBookmarks.getForestKeyCode() == GLFW.GLFW_KEY_R) {
            ForestBookmarks.setForestKeyCode(ForestBookmarks.DEFAULT_FOREST_KEY_CODE);
        }
        if (list == null) {
            return;
        }
        for (Entry entry : list.children()) {
            if (entry instanceof GroupNameWidget group && RECIPE_FOREST$GROUP_ID.equals(group.id)) {
                recipeForest$groupCollapsed = group.collapsed;
                return;
            }
        }
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void recipeForest$addSettingsGroup(CallbackInfo ci) {
        List<Entry> entries = list.children();
        int devIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof GroupNameWidget group && "dev".equals(group.id)) {
                devIndex = i;
                break;
            }
        }
        if (devIndex < 0) {
            throw new IllegalStateException("Incompatible EMI config layout. RecipeForest supports EMI 1.1.13-1.1.24; "
                    + "expected top-level ConfigScreen group id 'dev'.");
        }

        Supplier<String> currentSearch = () -> search.getSearch();
        GroupNameWidget group = new GroupNameWidget(RECIPE_FOREST$GROUP_ID,
                Component.translatable("screen.emi_recipeforest.settings.title"));
        group.collapsed = recipeForest$groupCollapsed;

        List<ConfigEntryWidget> settings = new ArrayList<>();
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.resolution_scope"), currentSearch,
                () -> recipeForest$enumLabel("resolution_scope", ForestBookmarks.getResolutionScope()),
                () -> ForestBookmarks.setResolutionScope(recipeForest$next(ForestBookmarks.getResolutionScope()))));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.root_layout"), currentSearch,
                () -> recipeForest$enumLabel("root_layout", ForestBookmarks.getRootLayout()),
                () -> ForestBookmarks.setRootLayout(recipeForest$next(ForestBookmarks.getRootLayout()))));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.quantity_mode"), currentSearch,
                () -> recipeForest$enumLabel("quantity_mode", ForestBookmarks.getQuantityMode()),
                () -> ForestBookmarks.setQuantityMode(recipeForest$next(ForestBookmarks.getQuantityMode()))));
        settings.add(new RecipeForestKeyEntry(currentSearch, () -> recipeForest$capturingKey,
                () -> recipeForest$keyConflict, () -> {
                    recipeForest$keyConflict = false;
                    recipeForest$capturingKey = true;
                }, () -> {
                    ForestBookmarks.setForestKeyCode(ForestBookmarks.DEFAULT_FOREST_KEY_CODE);
                    recipeForest$capturingKey = false;
                    recipeForest$keyConflict = false;
                }));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.box_enabled"), currentSearch,
                () -> Component.translatable(ForestBookmarks.isBoxEnabled()
                        ? "screen.emi_recipeforest.settings.enabled"
                        : "screen.emi_recipeforest.settings.disabled"),
                () -> ForestBookmarks.setBoxEnabled(!ForestBookmarks.isBoxEnabled())));
        settings.add(new RecipeForestStepperEntry(
                Component.translatable("screen.emi_recipeforest.settings.stacks_per_box", ""), currentSearch,
                ForestBookmarks::getStacksPerBox, ForestBookmarks::setStacksPerBox, 1, 256, () -> true));
        settings.add(new RecipeForestStepperEntry(
                Component.translatable("screen.emi_recipeforest.settings.columns", ""), currentSearch,
                ForestBookmarks::getRootGridColumns,
                value -> ForestBookmarks.setRootGridSize(value, ForestBookmarks.getRootGridRows()),
                1, 16, () -> ForestBookmarks.getRootLayout() == RootLayout.GRID));
        settings.add(new RecipeForestStepperEntry(
                Component.translatable("screen.emi_recipeforest.settings.rows", ""), currentSearch,
                ForestBookmarks::getRootGridRows,
                value -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns(), value),
                1, 8, () -> ForestBookmarks.getRootLayout() == RootLayout.GRID));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.title"), currentSearch,
                () -> Component.translatable("screen.emi_recipeforest.settings.forest_key.reset"),
                this::recipeForest$resetSettings));

        List<Entry> inserted = new ArrayList<>(settings.size() + 1);
        inserted.add(group);
        list.addEntry(group);
        for (ConfigEntryWidget setting : settings) {
            group.children.add(setting);
            setting.parentGroups.add(group);
            inserted.add(setting);
            list.addEntry(setting);
        }

        entries.removeAll(inserted);
        entries.addAll(devIndex, inserted);
    }

    @Unique
    private void recipeForest$resetSettings() {
        ForestBookmarks.setResolutionScope(ForestBookmarks.ResolutionScope.ALL_ROOTS);
        ForestBookmarks.setRootLayout(RootLayout.LIST);
        ForestBookmarks.setQuantityMode(ForestBookmarks.QuantityMode.ICON);
        ForestBookmarks.setForestKeyCode(ForestBookmarks.DEFAULT_FOREST_KEY_CODE);
        ForestBookmarks.setBoxEnabled(true);
        ForestBookmarks.setStacksPerBox(27);
        ForestBookmarks.setRootGridSize(8, 2);
        recipeForest$capturingKey = false;
        recipeForest$keyConflict = false;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void recipeForest$captureKey(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!recipeForest$capturingKey) {
            return;
        }
        recipeForest$consumeNextKeyRelease = true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            recipeForest$capturingKey = false;
            recipeForest$keyConflict = false;
        } else if (keyCode == GLFW.GLFW_KEY_R) {
            recipeForest$capturingKey = false;
            recipeForest$keyConflict = true;
        } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN && !recipeForest$isModifier(keyCode)) {
            ForestBookmarks.setForestKeyCode(keyCode);
            recipeForest$capturingKey = false;
            recipeForest$keyConflict = false;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void recipeForest$consumeCapturedKeyRelease(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (recipeForest$capturingKey || recipeForest$consumeNextKeyRelease) {
            recipeForest$consumeNextKeyRelease = false;
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean recipeForest$isModifier(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Unique
    private static <E extends Enum<E>> E recipeForest$next(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    @Unique
    private static Component recipeForest$enumLabel(String setting, Enum<?> value) {
        return Component.translatable("screen.emi_recipeforest.settings." + setting + "."
                + value.name().toLowerCase(Locale.ROOT));
    }

    @Unique
    private static final class RecipeForestKeyEntry extends ConfigEntryWidget {
        private final BooleanSupplier capturing;
        private final BooleanSupplier conflict;
        private final Button keyButton;
        private final Button resetButton;

        private RecipeForestKeyEntry(Supplier<String> currentSearch, BooleanSupplier capturing,
                BooleanSupplier conflict, Runnable beginCapture, Runnable reset) {
            super(Component.translatable("screen.emi_recipeforest.settings.forest_key"), List.of(), currentSearch,
                    RECIPE_FOREST$BUTTON_HEIGHT);
            this.capturing = capturing;
            this.conflict = conflict;
            keyButton = Button.builder(Component.empty(), button -> beginCapture.run())
                    .bounds(0, 0, 98, RECIPE_FOREST$BUTTON_HEIGHT).build();
            resetButton = Button.builder(
                    Component.translatable("screen.emi_recipeforest.settings.forest_key.reset"),
                    button -> reset.run()).bounds(0, 0, 48, RECIPE_FOREST$BUTTON_HEIGHT).build();
            setChildren(List.of(keyButton, resetButton));
        }

        @Override
        public void update(int y, int x, int width, int height) {
            int left = x + width - RECIPE_FOREST$CONTROL_WIDTH;
            keyButton.setX(left);
            keyButton.setY(y);
            keyButton.setMessage(conflict.getAsBoolean()
                    ? Component.translatable("screen.emi_recipeforest.settings.forest_key.conflict_r")
                    : capturing.getAsBoolean()
                            ? Component.translatable("screen.emi_recipeforest.settings.forest_key.capture")
                            : InputConstants.Type.KEYSYM.getOrCreate(ForestBookmarks.getForestKeyCode())
                                    .getDisplayName());
            resetButton.setX(left + 102);
            resetButton.setY(y);
        }
    }

    @Unique
    private static class RecipeForestValueEntry extends ConfigEntryWidget {
        private final Supplier<Component> value;
        private final Button button;

        private RecipeForestValueEntry(Component name, Supplier<String> currentSearch,
                Supplier<Component> value, Runnable onPress) {
            super(name, List.of(), currentSearch, RECIPE_FOREST$BUTTON_HEIGHT);
            this.value = value;
            button = Button.builder(value.get(), ignored -> onPress.run())
                    .bounds(0, 0, RECIPE_FOREST$CONTROL_WIDTH, RECIPE_FOREST$BUTTON_HEIGHT).build();
            setChildren(List.of(button));
        }

        @Override
        public void update(int y, int x, int width, int height) {
            button.setX(x + width - button.getWidth());
            button.setY(y);
            button.setMessage(value.get());
        }
    }

    @Unique
    private static final class RecipeForestStepperEntry extends ConfigEntryWidget {
        private final IntSupplier value;
        private final IntConsumer setter;
        private final int minimum;
        private final int maximum;
        private final BooleanSupplier visible;
        private final Button decrease;
        private final Button display;
        private final Button increase;

        private RecipeForestStepperEntry(Component name, Supplier<String> currentSearch, IntSupplier value,
                IntConsumer setter, int minimum, int maximum, BooleanSupplier visible) {
            super(name, List.of(), currentSearch, RECIPE_FOREST$BUTTON_HEIGHT);
            this.value = value;
            this.setter = setter;
            this.minimum = minimum;
            this.maximum = maximum;
            this.visible = visible;
            decrease = Button.builder(Component.translatable("screen.emi_recipeforest.settings.columns.decrease"),
                    ignored -> setter.accept(value.getAsInt() - 1))
                    .bounds(0, 0, 20, RECIPE_FOREST$BUTTON_HEIGHT).build();
            display = Button.builder(Component.empty(), ignored -> {
            }).bounds(0, 0, 106, RECIPE_FOREST$BUTTON_HEIGHT).build();
            display.active = false;
            increase = Button.builder(Component.translatable("screen.emi_recipeforest.settings.columns.increase"),
                    ignored -> setter.accept(value.getAsInt() + 1))
                    .bounds(0, 0, 20, RECIPE_FOREST$BUTTON_HEIGHT).build();
            setChildren(List.of(decrease, display, increase));
        }

        @Override
        public void update(int y, int x, int width, int height) {
            int current = value.getAsInt();
            int left = x + width - RECIPE_FOREST$CONTROL_WIDTH;
            decrease.setX(left);
            decrease.setY(y);
            decrease.active = current > minimum;
            display.setX(left + 22);
            display.setY(y);
            display.setMessage(Component.literal(Integer.toString(current)));
            increase.setX(left + 130);
            increase.setY(y);
            increase.active = current < maximum;
        }

        @Override
        public int getHeight() {
            return visible.getAsBoolean() ? super.getHeight() : 0;
        }
    }
}
