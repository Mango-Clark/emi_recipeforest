package io.github.mango_clark.emirecipeforest.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.ConfigEnumScreen;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.ConfigScreen.Mutator;
import dev.emi.emi.screen.widget.config.BooleanWidget;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.EmiBindWidget;
import dev.emi.emi.screen.widget.config.ConfigJumpButton;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntEdit;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.emi.emi.screen.widget.config.ListWidget.Entry;
import dev.emi.emi.screen.widget.config.SubGroupNameWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.RootLayout;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    @Unique
    private static final ResourceLocation RECIPE_FOREST$WIDGETS = EmiPort.id("emi_recipeforest",
            "textures/gui/widgets.png");

    @Shadow
    public ListWidget list;
    @Shadow
    private ConfigSearch search;
    @Shadow
    public EmiBind activeBind;

    @Unique
    private boolean recipeForest$groupCollapsed;
    @Unique
    private boolean recipeForest$forestBindWasActive;
    @Unique
    private boolean recipeForest$anyBindWasActive;
    @Unique
    private int recipeForest$collisionRevision;

    protected ConfigScreenMixin(Component title) {
        super(title);
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
        RecipeForestStepperEntry columns = new RecipeForestStepperEntry(
                Component.translatable("screen.emi_recipeforest.settings.columns", ""), currentSearch,
                recipeForest$tooltip("screen.emi_recipeforest.settings.columns.tooltip"),
                ForestBookmarks::getRootGridColumns,
                value -> ForestBookmarks.setRootGridSize(value, ForestBookmarks.getRootGridRows()),
                1, 16);
        RecipeForestStepperEntry rows = new RecipeForestStepperEntry(
                Component.translatable("screen.emi_recipeforest.settings.rows", ""), currentSearch,
                recipeForest$tooltip("screen.emi_recipeforest.settings.rows.tooltip"),
                ForestBookmarks::getRootGridRows,
                value -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns(), value),
                1, 8);
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
        for (ConfigEntryWidget setting : List.of(columns, rows)) {
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

    @Inject(method = "addJumpButtons()V", at = @At("RETURN"))
    private void recipeForest$addJumpButton(CallbackInfo ci) {
        boolean hasDevGroup = list.children().stream()
                .anyMatch(entry -> entry instanceof GroupNameWidget group && "dev".equals(group.id));
        List<ConfigJumpButton> nativeButtons = children().stream()
                .filter(ConfigJumpButton.class::isInstance).map(ConfigJumpButton.class::cast).toList();
        if (!hasDevGroup || nativeButtons.isEmpty()) {
            throw new IllegalStateException("Incompatible EMI config index. RecipeForest supports EMI 1.1.13-1.1.24; "
                    + "expected ConfigScreen.addJumpButtons() to create a final Dev ConfigJumpButton.");
        }

        ConfigJumpButton dev = nativeButtons.get(nativeButtons.size() - 1);
        int recipeForestY = dev.getY() - 8;
        for (ConfigJumpButton button : nativeButtons) {
            button.setY(button.getY() - 8);
        }
        dev.setY(dev.getY() + 16);
        addRenderableWidget(new RecipeForestJumpButton(2, recipeForestY,
                () -> ((ConfigScreen) (Object) this).jump(RECIPE_FOREST$GROUP_ID),
                () -> recipeForest$collisionRevision));
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
        recipeForest$forestBindWasActive |= activeBind == ForestBind.INSTANCE;
        recipeForest$anyBindWasActive |= activeBind != null;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void recipeForest$trackForestBindMouseEnd(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
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
    private static final class RecipeForestJumpButton extends AbstractButton {
        private final Runnable action;
        private final IntSupplier collisionRevision;
        private int lastCollisionRevision = -1;
        private List<ForestBind.Collision> collisions = List.of();

        private RecipeForestJumpButton(int x, int y, Runnable action, IntSupplier collisionRevision) {
            super(x, y, 16, 16, Component.translatable("screen.emi_recipeforest.settings.title"));
            this.action = action;
            this.collisionRevision = collisionRevision;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected void renderWidget(GuiGraphics raw, int mouseX, int mouseY, float delta) {
            refreshCollisions();
            EmiDrawContext context = EmiDrawContext.wrap(raw);
            if (!collisions.isEmpty()) {
                context.setColor(1, 0.65f, 0.2f);
            } else if (isMouseOver(mouseX, mouseY)) {
                context.setColor(0.5f, 0.6f, 1f);
            }
            context.push();
            context.matrices().translate(0, 0, 100);
            context.drawTexture(RECIPE_FOREST$WIDGETS, getX(), getY(), 0, 0, 16, 16, 16, 64, 32);
            context.pop();
            context.resetColor();

            if (isMouseOver(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = new ArrayList<>();
                tooltip.add(ClientTooltipComponent.create(getMessage().getVisualOrderText()));
                tooltip.addAll(recipeForest$tooltip("screen.emi_recipeforest.settings.jump.tooltip"));
                for (ForestBind.Collision collision : collisions) {
                    tooltip.add(ClientTooltipComponent.create(Component.translatable(
                            "screen.emi_recipeforest.settings.forest_key.override", collision.translatedName())
                            .withStyle(ChatFormatting.GOLD).getVisualOrderText()));
                }
                context.push();
                RenderSystem.disableDepthTest();
                EmiRenderHelper.drawTooltip(Minecraft.getInstance().screen, context, tooltip, mouseX, mouseY);
                RenderSystem.enableDepthTest();
                context.pop();
            }
        }

        private void refreshCollisions() {
            int revision = collisionRevision.getAsInt();
            if (revision != lastCollisionRevision) {
                lastCollisionRevision = revision;
                collisions = ForestBind.INSTANCE.getCollisions();
            }
        }
    }

    @Unique
    private static final class RecipeForestBindEntry extends EmiBindWidget {
        private final IntSupplier collisionRevision;
        private int lastCollisionRevision = -1;
        private List<ForestBind.Collision> collisions = List.of();
        private int statusX;
        private int statusY;

        private RecipeForestBindEntry(ConfigScreen screen, Supplier<String> currentSearch,
                IntSupplier collisionRevision) {
            super(screen, recipeForest$tooltip("screen.emi_recipeforest.settings.forest_key.tooltip"), currentSearch,
                    ForestBind.INSTANCE);
            this.collisionRevision = collisionRevision;
        }

        @Override
        public void update(int y, int x, int width, int height) {
            super.update(y, x, width, height);
            int revision = collisionRevision.getAsInt();
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
                context.drawTexture(RECIPE_FOREST$WIDGETS, statusX, statusY, 0, 0, 16, 16, 16, 64, 32);
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

    @Unique
    private static final class RecipeForestStepperEntry extends ConfigEntryWidget {
        private final IntSupplier value;
        private final IntConsumer setter;
        private final int minimum;
        private final int maximum;
        private final Button decrease;
        private final Button display;
        private final Button increase;

        private RecipeForestStepperEntry(Component name, Supplier<String> currentSearch,
                List<ClientTooltipComponent> tooltip, IntSupplier value,
                IntConsumer setter, int minimum, int maximum) {
            super(name, tooltip, currentSearch, RECIPE_FOREST$BUTTON_HEIGHT);
            this.value = value;
            this.setter = setter;
            this.minimum = minimum;
            this.maximum = maximum;
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
    }
}
