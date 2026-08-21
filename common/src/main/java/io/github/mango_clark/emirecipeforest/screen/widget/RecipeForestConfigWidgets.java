package io.github.mango_clark.emirecipeforest.screen.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import dev.emi.emi.config.IntGroup;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.ConfigEnumScreen;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.tooltip.EmiTooltip;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntGroupWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ListWidget.Entry;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.RootLayout;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import io.github.mango_clark.emirecipeforest.screen.RecipeForestTextures;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/** RecipeForest-owned controls composed from EMI's native config widgets. */
public final class RecipeForestConfigWidgets {
    public static final String GROUP_ID = "recipeforest";

    private static final int CONTROL_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private RecipeForestConfigWidgets() {
    }

    public static void addSettings(ConfigScreen screen, ListWidget list, ConfigSearch search,
            boolean groupCollapsed) {
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

        Supplier<String> currentSearch = search::getSearch;
        GroupNameWidget group = new GroupNameWidget(GROUP_ID,
                Component.translatable("screen.emi_recipeforest.settings.title"));
        group.collapsed = groupCollapsed;

        List<ConfigEntryWidget> settings = new ArrayList<>();
        settings.add(new ValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.resolution_scope"), currentSearch,
                tooltip("screen.emi_recipeforest.settings.resolution_scope.tooltip"),
                () -> enumLabel("resolution_scope", ForestBookmarks.getResolutionScope()),
                () -> openResolutionScopeScreen(screen)));
        settings.add(new ValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.root_layout"), currentSearch,
                tooltip("screen.emi_recipeforest.settings.root_layout.tooltip"),
                () -> enumLabel("root_layout", ForestBookmarks.getRootLayout()),
                () -> openEnumScreen(screen, "root_layout", RootLayout.values(), ForestBookmarks::setRootLayout)));
        settings.add(new ValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.quantity_mode"), currentSearch,
                tooltip("screen.emi_recipeforest.settings.quantity_mode.tooltip"),
                () -> enumLabel("quantity_mode", ForestBookmarks.getQuantityMode()),
                () -> openEnumScreen(screen, "quantity_mode", ForestBookmarks.QuantityMode.values(),
                        ForestBookmarks::setQuantityMode)));
        settings.add(new ForestBindEntry(screen, currentSearch));
        settings.add(new BooleanWidget(
                Component.translatable("screen.emi_recipeforest.settings.box_enabled"),
                tooltip("screen.emi_recipeforest.settings.box_enabled.tooltip"),
                currentSearch,
                screen.new Mutator<Boolean>() {
                    @Override
                    protected Boolean getValue() {
                        return ForestBookmarks.isBoxEnabled();
                    }

                    @Override
                    protected void setValue(Boolean value) {
                        ForestBookmarks.setBoxEnabled(value);
                    }
                }));
        settings.add(new IntWidget(
                Component.translatable("screen.emi_recipeforest.settings.stacks_per_box", ""),
                tooltip("screen.emi_recipeforest.settings.stacks_per_box.tooltip"), currentSearch,
                screen.new Mutator<Integer>() {
                    @Override
                    protected Integer getValue() {
                        return ForestBookmarks.getStacksPerBox();
                    }

                    @Override
                    protected void setValue(Integer value) {
                        ForestBookmarks.setStacksPerBox(Math.clamp(value, 1, 256));
                    }
                }));

        SubGroupNameWidget details = new SubGroupNameWidget(GROUP_ID + ".details",
                Component.translatable("screen.emi_recipeforest.settings.details"));
        details.parent = group;
        IntGroupWidget gridSize = new IntGroupWidget(
                Component.translatable("screen.emi_recipeforest.settings.grid_size"),
                tooltip("screen.emi_recipeforest.settings.grid_size.tooltip"), currentSearch,
                screen.new Mutator<IntGroup>() {
                    @Override
                    protected IntGroup getValue() {
                        return new IntGroup("screen.emi_recipeforest.settings.grid_size.",
                                List.of("columns", "rows"),
                                IntArrayList.of(ForestBookmarks.getRootGridColumns(),
                                        ForestBookmarks.getRootGridRows()));
                    }

                    @Override
                    protected void setValue(IntGroup value) {
                        int columns = Math.clamp(value.values.getInt(0), 1, 16);
                        int rows = Math.clamp(value.values.getInt(1), 1, 8);
                        value.values.set(0, columns);
                        value.values.set(1, rows);
                        ForestBookmarks.setRootGridSize(columns, rows);
                    }
                });
        IntWidget listLength = new IntWidget(
                Component.translatable("screen.emi_recipeforest.settings.list_length"),
                tooltip("screen.emi_recipeforest.settings.list_length.tooltip"), currentSearch,
                screen.new Mutator<Integer>() {
                    @Override
                    protected Integer getValue() {
                        return ForestBookmarks.getListLength();
                    }

                    @Override
                    protected void setValue(Integer value) {
                        int rows = Math.clamp(value, 1, ForestBookmarks.LIST_LENGTH_LIMIT);
                        ForestBookmarks.setListLength(rows);
                    }
                });
        settings.add(new ValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.title"), currentSearch,
                tooltip("screen.emi_recipeforest.settings.reset.tooltip"),
                () -> Component.translatable("screen.emi_recipeforest.settings.forest_key.reset")
                        .withStyle(ChatFormatting.RED),
                () -> confirmResetSettings(screen)));

        List<Entry> inserted = new ArrayList<>(settings.size() + 4);
        inserted.add(group);
        list.addEntry(group);
        for (ConfigEntryWidget setting : settings) {
            group.children.add(setting);
            setting.parentGroups.add(group);
            inserted.add(setting);
            list.addEntry(setting);
        }
        inserted.add(details);
        list.addEntry(details);
        for (ConfigEntryWidget setting : List.of(gridSize, listLength)) {
            group.children.add(setting);
            details.children.add(setting);
            setting.parentGroups.add(group);
            setting.parentGroups.add(details);
            inserted.add(setting);
            list.addEntry(setting);
        }

        entries.removeAll(inserted);
        entries.addAll(devIndex, inserted);
    }

    public static List<ConfigJumpButton> createJumpButtons(ConfigScreen screen) {
        List<ConfigJumpButton> nativeButtons = screen.children().stream()
                .filter(ConfigJumpButton.class::isInstance)
                .map(ConfigJumpButton.class::cast)
                .toList();
        if (nativeButtons.isEmpty()) {
            throw new IllegalStateException("Incompatible EMI config index. RecipeForest supports EMI 1.1.13-1.1.24; "
                    + "expected ConfigScreen.addJumpButtons() to create a final Dev ConfigJumpButton.");
        }

        ConfigJumpButton dev = nativeButtons.get(nativeButtons.size() - 1);
        int y = dev.getY();
        dev.setY(y + RecipeForestTextures.ICON_SIZE * 2);
        return List.of(
                new JumpButton(2, y, RecipeForestTextures.FOREST_ICON_U, RecipeForestTextures.FOREST_ICON_V,
                        button -> screen.jump(GROUP_ID),
                        Component.translatable("screen.emi_recipeforest.settings.title"), true),
                new JumpButton(10, y + RecipeForestTextures.ICON_SIZE,
                        RecipeForestTextures.DETAILS_ICON_U, RecipeForestTextures.DETAILS_ICON_V,
                        button -> screen.jump(GROUP_ID + ".details"),
                        Component.translatable("screen.emi_recipeforest.settings.details"), false));
    }

    private static void openResolutionScopeScreen(ConfigScreen screen) {
        List<ConfigEnumScreen.Entry<ResolutionScope>> entries = new ArrayList<>();
        for (ResolutionScope scope : ResolutionScope.values()) {
            String key = "screen.emi_recipeforest.settings.resolution_scope."
                    + scope.name().toLowerCase(Locale.ROOT);
            entries.add(new ConfigEnumScreen.Entry<>(scope, Component.translatable(key), tooltip(key + ".tooltip")));
        }
        Minecraft.getInstance().setScreen(new ConfigEnumScreen<>(screen, entries,
                ForestBookmarks::setResolutionScope));
    }

    private static <E extends Enum<E>> void openEnumScreen(ConfigScreen screen, String setting, E[] values,
            java.util.function.Consumer<E> setter) {
        List<ConfigEnumScreen.Entry<E>> entries = new ArrayList<>();
        for (E value : values) {
            String key = "screen.emi_recipeforest.settings." + setting + "."
                    + value.name().toLowerCase(Locale.ROOT);
            entries.add(new ConfigEnumScreen.Entry<>(value, Component.translatable(key), tooltip(key + ".tooltip")));
        }
        Minecraft.getInstance().setScreen(new ConfigEnumScreen<>(screen, entries, setter));
    }

    private static void confirmResetSettings(ConfigScreen screen) {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                resetSettings();
            }
            client.setScreen(screen);
        }, Component.translatable("screen.emi_recipeforest.settings.reset.confirm.title"),
                Component.translatable("screen.emi_recipeforest.settings.reset.confirm.body")));
    }

    private static void resetSettings() {
        ForestBookmarks.setResolutionScope(ResolutionScope.ALL_ROOTS);
        ForestBookmarks.setRootLayout(RootLayout.LIST);
        ForestBookmarks.setQuantityMode(ForestBookmarks.QuantityMode.ICON);
        ForestBind.INSTANCE.setToDefault();
        ForestBookmarks.setBoxEnabled(true);
        ForestBookmarks.setStacksPerBox(27);
        ForestBookmarks.setRootGridSize(8, 2);
        ForestBookmarks.setListLength(ForestBookmarks.DEFAULT_LIST_LENGTH);
    }

    private static Component enumLabel(String setting, Enum<?> value) {
        return Component.translatable("screen.emi_recipeforest.settings." + setting + "."
                + value.name().toLowerCase(Locale.ROOT));
    }

    private static List<ClientTooltipComponent> tooltip(String key) {
        return EmiTooltip.splitTranslate(key);
    }

    private static final class JumpButton extends ConfigJumpButton {
        private final Component title;
        private final boolean showCollisions;

        private JumpButton(int x, int y, int u, int v, OnPress action, Component title,
                boolean showCollisions) {
            super(x, y, u, v, action, List.of(title));
            this.title = title;
            this.showCollisions = showCollisions;
            this.texture = RecipeForestTextures.WIDGETS;
            if (showCollisions) {
                this.text = this::tooltip;
            }
        }

        @Override
        public void renderWidget(GuiGraphics raw, int mouseX, int mouseY, float delta) {
            if (showCollisions && !ForestBind.INSTANCE.getCollisions().isEmpty()) {
                EmiDrawContext.wrap(raw).setColor(1, 0.65f, 0.2f);
            }
            super.renderWidget(raw, mouseX, mouseY, delta);
            EmiDrawContext.wrap(raw).resetColor();
        }

        private List<Component> tooltip() {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(title);
            for (ForestBind.Collision collision : ForestBind.INSTANCE.getCollisions()) {
                tooltip.add(Component.translatable(
                        "screen.emi_recipeforest.settings.forest_key.override", collision.translatedName())
                        .withStyle(ChatFormatting.GOLD));
            }
            return tooltip;
        }
    }

    private static final class ForestBindEntry extends EmiBindWidget {
        private int statusX;
        private int statusY;

        private ForestBindEntry(ConfigScreen screen, Supplier<String> currentSearch) {
            super(screen, tooltip("screen.emi_recipeforest.settings.forest_key.tooltip"), currentSearch,
                    ForestBind.INSTANCE);
        }

        @Override
        public void update(int y, int x, int width, int height) {
            super.update(y, x, width, height);
            statusX = x + width - 244;
            statusY = y + 2;
        }

        @Override
        public void render(GuiGraphics raw, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            super.render(raw, index, y, x, width, height, mouseX, mouseY, hovered, delta);
            if (!ForestBind.INSTANCE.getCollisions().isEmpty()) {
                EmiDrawContext context = EmiDrawContext.wrap(raw);
                context.setColor(1, 0.65f, 0.2f);
                context.drawTexture(RecipeForestTextures.WIDGETS, statusX, statusY,
                        RecipeForestTextures.FOREST_ICON_U, RecipeForestTextures.FOREST_ICON_V,
                        RecipeForestTextures.ICON_SIZE, RecipeForestTextures.ICON_SIZE);
                context.resetColor();
            }
        }

        @Override
        public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
            List<ClientTooltipComponent> tooltip = new ArrayList<>(super.getTooltip(mouseX, mouseY));
            for (ForestBind.Collision collision : ForestBind.INSTANCE.getCollisions()) {
                tooltip.add(ClientTooltipComponent.create(Component.translatable(
                        "screen.emi_recipeforest.settings.forest_key.override", collision.translatedName())
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText()));
            }
            return List.copyOf(tooltip);
        }
    }

    private static final class ValueEntry extends ConfigEntryWidget {
        private final Supplier<Component> value;
        private final Button button;

        private ValueEntry(Component name, Supplier<String> currentSearch, List<ClientTooltipComponent> tooltip,
                Supplier<Component> value, Runnable onPress) {
            super(name, tooltip, currentSearch, BUTTON_HEIGHT);
            this.value = value;
            button = Button.builder(value.get(), ignored -> onPress.run())
                    .bounds(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT).build();
            setChildren(List.of(button));
        }

        @Override
        public void update(int y, int x, int width, int height) {
            button.setX(x + width - button.getWidth());
            button.setY(y);
            button.setMessage(value.get());
        }
    }
}
