package io.github.mango_clark.emirecipeforest.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import dev.emi.emi.EmiPort;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.IntGroup;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.ConfigEnumScreen;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntEdit;
import dev.emi.emi.screen.widget.config.IntGroupWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ListWidget.Entry;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ConfigState;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.RootLayout;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import io.github.mango_clark.emirecipeforest.screen.RecipeForestTextures;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
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
    @Shadow
    public EmiBind activeBind;
    @Shadow
    public String originalConfig;
    @Shadow
    public Button resetButton;

    @Unique
    private boolean recipeForest$groupCollapsed;
    @Unique
    private boolean recipeForest$forestBindWasActive;
    @Unique
    private boolean recipeForest$anyBindWasActive;
    @Unique
    private int recipeForest$collisionRevision;
    @Unique
    private ConfigState recipeForest$originalConfig;
    @Unique
    private boolean recipeForest$revertClicked;

    protected ConfigScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void recipeForest$captureOriginalConfig(Screen last, CallbackInfo ci) {
        recipeForest$originalConfig = ForestBookmarks.captureConfigState();
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void recipeForest$rememberGroupState(CallbackInfo ci) {
        recipeForest$forestBindWasActive = false;
        recipeForest$anyBindWasActive = false;
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
                recipeForest$tooltip("screen.emi_recipeforest.settings.resolution_scope.tooltip"),
                () -> recipeForest$enumLabel("resolution_scope", ForestBookmarks.getResolutionScope()),
                this::recipeForest$openResolutionScopeScreen));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.root_layout"), currentSearch,
                recipeForest$tooltip("screen.emi_recipeforest.settings.root_layout.tooltip"),
                () -> recipeForest$enumLabel("root_layout", ForestBookmarks.getRootLayout()),
                this::recipeForest$openRootLayoutScreen));
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.quantity_mode"), currentSearch,
                recipeForest$tooltip("screen.emi_recipeforest.settings.quantity_mode.tooltip"),
                () -> recipeForest$enumLabel("quantity_mode", ForestBookmarks.getQuantityMode()),
                this::recipeForest$openQuantityModeScreen));
        settings.add(new RecipeForestBindEntry((ConfigScreen) (Object) this, currentSearch,
                () -> recipeForest$collisionRevision));
        settings.add(new BooleanWidget(
                Component.translatable("screen.emi_recipeforest.settings.box_enabled"),
                recipeForest$tooltip("screen.emi_recipeforest.settings.box_enabled.tooltip"),
                currentSearch,
                ((ConfigScreen) (Object) this).new Mutator<Boolean>() {
                    @Override
                    protected Boolean getValue() {
                        return ForestBookmarks.isBoxEnabled();
                    }

                    @Override
                    protected void setValue(Boolean value) {
                        ForestBookmarks.setBoxEnabled(value);
                    }
                }));
        settings.add(new RecipeForestIntEntry(
                Component.translatable("screen.emi_recipeforest.settings.stacks_per_box", ""), currentSearch));
        SubGroupNameWidget details = new SubGroupNameWidget(RECIPE_FOREST$GROUP_ID + ".details",
                Component.translatable("screen.emi_recipeforest.settings.details"));
        details.parent = group;
        IntGroupWidget gridSize = new IntGroupWidget(
                Component.translatable("screen.emi_recipeforest.settings.grid_size"),
                recipeForest$tooltip("screen.emi_recipeforest.settings.grid_size.tooltip"), currentSearch,
                ((ConfigScreen) (Object) this).new Mutator<IntGroup>() {
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
                recipeForest$tooltip("screen.emi_recipeforest.settings.list_length.tooltip"), currentSearch,
                ((ConfigScreen) (Object) this).new Mutator<Integer>() {
                    @Override
                    protected Integer getValue() {
                        return ForestBookmarks.getListLength();
                    }

                    @Override
                    protected void setValue(Integer value) {
                        int rows = Math.max(1, Math.min(ForestBookmarks.LIST_LENGTH_LIMIT, value));
                        ForestBookmarks.setListLength(rows);
                    }
                });
        settings.add(new RecipeForestValueEntry(
                Component.translatable("screen.emi_recipeforest.settings.title"), currentSearch,
                recipeForest$tooltip("screen.emi_recipeforest.settings.reset.tooltip"),
                () -> Component.translatable("screen.emi_recipeforest.settings.forest_key.reset")
                        .withStyle(ChatFormatting.RED),
                this::recipeForest$confirmResetSettings));

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

    @Inject(method = "updateChanges", at = @At("RETURN"))
    private void recipeForest$includeConfigChanges(CallbackInfo ci) {
        String[] originalLines = originalConfig.split("\n\n");
        String[] currentLines = EmiConfig.getSavedConfig().split("\n\n");
        int changes = 0;
        int sectionCount = Math.max(originalLines.length, currentLines.length);
        for (int i = 0; i < sectionCount; i++) {
            if (i >= originalLines.length || i >= currentLines.length
                    || !originalLines[i].equals(currentLines[i])) {
                changes++;
            }
        }
        changes += recipeForest$originalConfig.countChanges(ForestBookmarks.captureConfigState());
        resetButton.active = changes > 0;
        resetButton.setMessage(EmiPort.translatable("screen.emi.config.reset", changes));
    }

    @Inject(method = "addJumpButtons()V", at = @At("HEAD"), cancellable = true)
    private void recipeForest$replaceJumpButtons(CallbackInfo ci) {
        List<String> jumps = new ArrayList<>(List.of(
                "general", "general.search",
                "ui", "ui.left-sidebar", "ui.right-sidebar", "ui.top-sidebar", "ui.bottom-sidebar",
                "binds", "binds.crafts", "binds.cheats",
                RECIPE_FOREST$GROUP_ID, RECIPE_FOREST$GROUP_ID + ".details",
                "dev"
        ));
        List<List<String>> removes = List.of(
                List.of("binds.cheats"),
                List.of("general.search"),
                List.of("ui.top-sidebar", "ui.bottom-sidebar"),
                List.of(RECIPE_FOREST$GROUP_ID + ".details"),
                List.of("binds.crafts"),
                List.of("ui.left-sidebar", "ui.right-sidebar")
        );
        int space = list.getLogicalHeight() - 10;
        for (List<String> remove : removes) {
            if (jumps.size() * 16 > space) {
                jumps.removeAll(remove);
            }
        }
        int y = 40 + (list.getLogicalHeight() - jumps.size() * 16) / 2;
        int emiU = 0;
        int emiV = -16;
        for (String jump : jumps) {
            boolean newGroup = !jump.contains(".");
            int x = 2 + (newGroup ? 0 : 8);
            if (jump.equals(RECIPE_FOREST$GROUP_ID)) {
                addRenderableWidget(new RecipeForestWarningJumpButton(
                        x, y,
                        () -> ((ConfigScreen) (Object) this).jump(RECIPE_FOREST$GROUP_ID),
                        () -> recipeForest$collisionRevision,
                        RecipeForestTextures.FOREST_ICON_U, RecipeForestTextures.FOREST_ICON_V,
                        "screen.emi_recipeforest.settings.title"));
                y += RecipeForestTextures.ICON_SIZE;
                continue;
            }
            if (jump.equals(RECIPE_FOREST$GROUP_ID + ".details")) {
                addRenderableWidget(new RecipeForestJumpButton(
                        x, y,
                        () -> ((ConfigScreen) (Object) this).jump(RECIPE_FOREST$GROUP_ID + ".details"),
                        RecipeForestTextures.DETAILS_ICON_U, RecipeForestTextures.DETAILS_ICON_V,
                        "screen.emi_recipeforest.settings.details"));
                y += RecipeForestTextures.ICON_SIZE;
                continue;
            }
            if (newGroup) {
                emiV += 16;
                emiU = 0;
            } else {
                emiU += 16;
            }
            addRenderableWidget(new ConfigJumpButton(
                    x, y, emiU, emiV,
                    button -> ((ConfigScreen) (Object) this).jump(jump),
                    List.of(EmiPort.translatable("config.emi.group." + jump.replace('-', '_')))));
            y += 16;
        }
        ci.cancel();
    }

    @Unique
    private void recipeForest$openResolutionScopeScreen() {
        List<ConfigEnumScreen.Entry<ResolutionScope>> entries = new ArrayList<>();
        for (ResolutionScope scope : ResolutionScope.values()) {
            String key = "screen.emi_recipeforest.settings.resolution_scope."
                    + scope.name().toLowerCase(Locale.ROOT);
            entries.add(new ConfigEnumScreen.Entry<>(scope, Component.translatable(key),
                    List.of(ClientTooltipComponent.create(
                            Component.translatable(key + ".tooltip").getVisualOrderText()))));
        }
        minecraft.setScreen(new ConfigEnumScreen<>((ConfigScreen) (Object) this, entries,
                ForestBookmarks::setResolutionScope));
    }

    @Unique
    private void recipeForest$openRootLayoutScreen() {
        recipeForest$openEnumScreen("root_layout", RootLayout.values(), ForestBookmarks::setRootLayout);
    }

    @Unique
    private void recipeForest$openQuantityModeScreen() {
        recipeForest$openEnumScreen("quantity_mode", ForestBookmarks.QuantityMode.values(),
                ForestBookmarks::setQuantityMode);
    }

    @Unique
    private <E extends Enum<E>> void recipeForest$openEnumScreen(String setting, E[] values,
            java.util.function.Consumer<E> setter) {
        List<ConfigEnumScreen.Entry<E>> entries = new ArrayList<>();
        for (E value : values) {
            String key = "screen.emi_recipeforest.settings." + setting + "."
                    + value.name().toLowerCase(Locale.ROOT);
            entries.add(new ConfigEnumScreen.Entry<>(value, Component.translatable(key), List.of()));
        }
        minecraft.setScreen(new ConfigEnumScreen<>((ConfigScreen) (Object) this, entries, setter));
    }

    @Unique
    private void recipeForest$confirmResetSettings() {
        ConfigScreen current = (ConfigScreen) (Object) this;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                recipeForest$resetSettings();
            }
            minecraft.setScreen(current);
        }, Component.translatable("screen.emi_recipeforest.settings.reset.confirm.title"),
                Component.translatable("screen.emi_recipeforest.settings.reset.confirm.body")));
    }

    @Unique
    private void recipeForest$resetSettings() {
        ForestBookmarks.setResolutionScope(ForestBookmarks.ResolutionScope.ALL_ROOTS);
        ForestBookmarks.setRootLayout(RootLayout.LIST);
        ForestBookmarks.setQuantityMode(ForestBookmarks.QuantityMode.ICON);
        ForestBind.INSTANCE.setToDefault();
        ForestBookmarks.setBoxEnabled(true);
        ForestBookmarks.setStacksPerBox(27);
        ForestBookmarks.setRootGridSize(8, 2);
        ForestBookmarks.setListLength(ForestBookmarks.DEFAULT_LIST_LENGTH);
    }

    @Inject(method = { "keyPressed", "keyReleased" }, at = @At("HEAD"))
    private void recipeForest$trackForestBindKeyStart(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$forestBindWasActive |= activeBind == ForestBind.INSTANCE;
        recipeForest$anyBindWasActive |= activeBind != null;
    }

    @Inject(method = { "keyPressed", "keyReleased" }, at = @At("RETURN"))
    private void recipeForest$trackForestBindKeyEnd(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$persistFinishedForestBind();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void recipeForest$trackForestBindMouseStart(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        recipeForest$revertClicked = button == 0 && activeBind == null && resetButton != null && resetButton.active
                && resetButton.isMouseOver(mouseX, mouseY);
        if (recipeForest$revertClicked) {
            ForestBookmarks.restoreConfigState(recipeForest$originalConfig);
            ForestBind.INSTANCE.reloadFromBookmarks();
            recipeForest$collisionRevision++;
        }
        recipeForest$forestBindWasActive |= activeBind == ForestBind.INSTANCE;
        recipeForest$anyBindWasActive |= activeBind != null;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void recipeForest$trackForestBindMouseEnd(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (recipeForest$revertClicked) {
            recipeForest$revertClicked = false;
            ((ConfigScreen) (Object) this).updateChanges();
        }
        recipeForest$persistFinishedForestBind();
    }

    @Unique
    private void recipeForest$persistFinishedForestBind() {
        if (recipeForest$anyBindWasActive && activeBind == null) {
            recipeForest$anyBindWasActive = false;
            recipeForest$collisionRevision++;
        }
        if (recipeForest$forestBindWasActive && activeBind != ForestBind.INSTANCE) {
            recipeForest$forestBindWasActive = false;
            ForestBind.INSTANCE.setBinds(ForestBind.INSTANCE.boundKeys.stream()
                    .filter(key -> !key.isUnbound()).toArray(EmiBind.ModifiedKey[]::new));
        }
    }

    @Unique
    private static Component recipeForest$enumLabel(String setting, Enum<?> value) {
        return Component.translatable("screen.emi_recipeforest.settings." + setting + "."
                + value.name().toLowerCase(Locale.ROOT));
    }

    @Unique
    private static List<ClientTooltipComponent> recipeForest$tooltip(String key) {
        return List.of(ClientTooltipComponent.create(Component.translatable(key).getVisualOrderText()));
    }

    @Unique
    private static class RecipeForestJumpButton extends SizedButtonWidget {
        protected final Component tooltipTitle;

        private RecipeForestJumpButton(int x, int y, Runnable action, int u, int v, String titleKey) {
            this(x, y, action, u, v, Component.translatable(titleKey));
        }

        private RecipeForestJumpButton(int x, int y, Runnable action, int u, int v, Component title) {
            super(x, y, RecipeForestTextures.ICON_SIZE, RecipeForestTextures.ICON_SIZE, u, v,
                    () -> true, button -> action.run(), List.of(title));
            this.tooltipTitle = title;
            this.texture = RecipeForestTextures.WIDGETS;
        }

        @Override
        protected int getV(int mouseX, int mouseY) {
            return this.v;
        }

        protected boolean hasWarning() {
            return false;
        }

        @Override
        public void renderWidget(GuiGraphics raw, int mouseX, int mouseY, float delta) {
            EmiDrawContext context = EmiDrawContext.wrap(raw);
            if (hasWarning()) {
                context.setColor(1, 0.65f, 0.2f);
            } else if (isMouseOver(mouseX, mouseY)) {
                context.setColor(0.5f, 0.6f, 1f);
            }
            context.push();
            context.matrices().translate(0, 0, 100);
            super.renderWidget(raw, mouseX, mouseY, delta);
            context.pop();
            context.resetColor();
        }
    }

    @Unique
    private static final class RecipeForestWarningJumpButton extends RecipeForestJumpButton {
        private final IntSupplier collisionRevisionSupplier;
        private int lastCollisionRevision = -1;
        private List<ForestBind.Collision> collisions = List.of();

        private RecipeForestWarningJumpButton(int x, int y, Runnable action,
                IntSupplier collisionRevisionSupplier, int u, int v, String titleKey) {
            super(x, y, action, u, v, titleKey);
            this.collisionRevisionSupplier = collisionRevisionSupplier;
            this.text = this::tooltip;
        }

        @Override
        protected boolean hasWarning() {
            refreshCollisions();
            return !collisions.isEmpty();
        }

        private List<Component> tooltip() {
            refreshCollisions();
            List<Component> tooltip = new ArrayList<>(1 + collisions.size());
            tooltip.add(tooltipTitle);
            for (ForestBind.Collision collision : collisions) {
                tooltip.add(Component.translatable(
                        "screen.emi_recipeforest.settings.forest_key.override", collision.translatedName())
                        .withStyle(ChatFormatting.GOLD));
            }
            return tooltip;
        }

        private void refreshCollisions() {
            int revision = collisionRevisionSupplier.getAsInt();
            if (revision != lastCollisionRevision) {
                lastCollisionRevision = revision;
                collisions = ForestBind.INSTANCE.getCollisions();
            }
        }
    }

    @Unique
    private static final class RecipeForestBindEntry extends EmiBindWidget {
        private final IntSupplier collisionRevisionSupplier;
        private int lastCollisionRevision = -1;
        private List<ForestBind.Collision> collisions = List.of();
        private int statusX;
        private int statusY;

        private RecipeForestBindEntry(ConfigScreen screen, Supplier<String> currentSearch,
                IntSupplier collisionRevisionSupplier) {
            super(screen, recipeForest$tooltip("screen.emi_recipeforest.settings.forest_key.tooltip"), currentSearch,
                    ForestBind.INSTANCE);
            this.collisionRevisionSupplier = collisionRevisionSupplier;
        }

        @Override
        public void update(int y, int x, int width, int height) {
            super.update(y, x, width, height);
            int revision = collisionRevisionSupplier.getAsInt();
            if (revision != lastCollisionRevision) {
                lastCollisionRevision = revision;
                collisions = ForestBind.INSTANCE.getCollisions();
            }
            statusX = x + width - 244;
            statusY = y + 2;
        }

        @Override
        public void render(GuiGraphics raw, int index, int y, int x, int width, int height, int mouseX, int mouseY,
                boolean hovered, float delta) {
            super.render(raw, index, y, x, width, height, mouseX, mouseY, hovered, delta);
            if (!collisions.isEmpty()) {
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
            for (ForestBind.Collision collision : collisions) {
                tooltip.add(ClientTooltipComponent.create(Component.translatable(
                        "screen.emi_recipeforest.settings.forest_key.override", collision.translatedName())
                        .withStyle(ChatFormatting.GOLD).getVisualOrderText()));
            }
            return List.copyOf(tooltip);
        }
    }

    @Unique
    private static final class RecipeForestIntEntry extends ConfigEntryWidget {
        private final IntEdit edit;

        private RecipeForestIntEntry(Component name, Supplier<String> currentSearch) {
            super(name, recipeForest$tooltip("screen.emi_recipeforest.settings.stacks_per_box.tooltip"), currentSearch,
                    RECIPE_FOREST$BUTTON_HEIGHT);
            edit = new IntEdit(RECIPE_FOREST$CONTROL_WIDTH, ForestBookmarks::getStacksPerBox,
                    value -> ForestBookmarks.setStacksPerBox(Math.max(1, Math.min(256, value))));
            setChildren(List.of(edit.text, edit.up, edit.down));
        }

        @Override
        public void update(int y, int x, int width, int height) {
            edit.setPosition(x + width - RECIPE_FOREST$CONTROL_WIDTH, y);
            if (!edit.text.isFocused()) {
                String value = Integer.toString(ForestBookmarks.getStacksPerBox());
                if (!value.equals(edit.text.getValue())) {
                    edit.text.setValue(value);
                }
            }
        }
    }

    @Unique
    private static class RecipeForestValueEntry extends ConfigEntryWidget {
        private final Supplier<Component> value;
        private final Button button;

        private RecipeForestValueEntry(Component name, Supplier<String> currentSearch,
                List<ClientTooltipComponent> tooltip,
                Supplier<Component> value, Runnable onPress) {
            super(name, tooltip, currentSearch, RECIPE_FOREST$BUTTON_HEIGHT);
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

}
