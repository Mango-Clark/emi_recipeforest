package io.github.mango_clark.emirecipeforest.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.screen.ConfigScreen;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.QuantityMode;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.RootLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds RecipeForest settings as a modal within EMI's existing config screen. */
@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin extends Screen {
    @Unique
    private static final int RECIPE_FOREST$PANEL_WIDTH = 300;
    @Unique
    private static final int RECIPE_FOREST$PANEL_HEIGHT = 250;
    @Unique
    private static final int RECIPE_FOREST$ROW_HEIGHT = 21;
    @Unique
    private boolean recipeForest$settingsOpen;
    @Unique
    private boolean recipeForest$capturingKey;
    @Unique
    private boolean recipeForest$consumeNextMouseRelease;
    @Unique
    private boolean recipeForest$consumeNextKeyRelease;
    @Unique
    private int recipeForest$scrollOffset;

    protected ConfigScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void recipeForest$addSettingsLauncher(CallbackInfo ci) {
        int buttonWidth = Math.min(140, Math.max(20, width - 8));
        addRenderableWidget(Button.builder(Component.translatable("tooltip.emi_recipeforest.settings"), button -> {
            recipeForest$settingsOpen = true;
            recipeForest$capturingKey = false;
        }).bounds(Math.max(4, width - buttonWidth - 4), 6, buttonWidth, 20).build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void recipeForest$renderSettings(GuiGraphics graphics, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        if (!recipeForest$settingsOpen) {
            return;
        }

        int left = recipeForest$panelLeft();
        int top = recipeForest$panelTop();
        int panelWidth = recipeForest$panelWidth();
        int panelHeight = recipeForest$panelHeight();
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF8099FF);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, 0xFF202020);
        graphics.drawCenteredString(font, Component.translatable("screen.emi_recipeforest.settings.title"),
                width / 2, top + 10, 0xFFFFFFFF);

        recipeForest$clampScroll();
        graphics.enableScissor(left + 2, recipeForest$contentTop(), left + panelWidth - 2,
                recipeForest$contentBottom());
        int row = 0;
        recipeForest$renderValueRow(graphics, mouseX, mouseY, row++,
                Component.translatable("screen.emi_recipeforest.settings.resolution_scope"),
                recipeForest$enumLabel("resolution_scope", ForestBookmarks.getResolutionScope()));
        recipeForest$renderValueRow(graphics, mouseX, mouseY, row++,
                Component.translatable("screen.emi_recipeforest.settings.root_layout"),
                recipeForest$enumLabel("root_layout", ForestBookmarks.getRootLayout()));
        recipeForest$renderValueRow(graphics, mouseX, mouseY, row++,
                Component.translatable("screen.emi_recipeforest.settings.quantity_mode"),
                recipeForest$enumLabel("quantity_mode", ForestBookmarks.getQuantityMode()));
        recipeForest$renderKeyRow(graphics, mouseX, mouseY, row++);
        recipeForest$renderValueRow(graphics, mouseX, mouseY, row++,
                Component.translatable("screen.emi_recipeforest.settings.box_enabled"),
                Component.translatable(ForestBookmarks.isBoxEnabled()
                        ? "screen.emi_recipeforest.settings.enabled"
                        : "screen.emi_recipeforest.settings.disabled"));
        recipeForest$renderStepperRow(graphics, mouseX, mouseY, row++,
                Component.translatable("screen.emi_recipeforest.settings.stacks_per_box",
                        ForestBookmarks.getStacksPerBox()),
                ForestBookmarks.getStacksPerBox() > 1, ForestBookmarks.getStacksPerBox() < 256);
        if (ForestBookmarks.getRootLayout() == RootLayout.GRID) {
            recipeForest$renderStepperRow(graphics, mouseX, mouseY, row++,
                    Component.translatable("screen.emi_recipeforest.settings.columns",
                            ForestBookmarks.getRootGridColumns()),
                    ForestBookmarks.getRootGridColumns() > 1, ForestBookmarks.getRootGridColumns() < 16);
            recipeForest$renderStepperRow(graphics, mouseX, mouseY, row,
                    Component.translatable("screen.emi_recipeforest.settings.rows",
                            ForestBookmarks.getRootGridRows()),
                    ForestBookmarks.getRootGridRows() > 1, ForestBookmarks.getRootGridRows() < 8);
        }
        graphics.disableScissor();

        recipeForest$renderButton(graphics, recipeForest$doneLeft(), top + panelHeight - 25,
                recipeForest$doneWidth(), 20, mouseX, mouseY, true,
                Component.translatable("screen.emi_recipeforest.settings.done"));
    }

    @Unique
    private void recipeForest$renderValueRow(GuiGraphics graphics, int mouseX, int mouseY, int row,
            Component label, Component value) {
        int y = recipeForest$rowY(row);
        graphics.drawString(font, label, recipeForest$panelLeft() + 10, y + 6, 0xFFFFFFFF, false);
        int valueLeft = recipeForest$valueLeft();
        recipeForest$renderButton(graphics, valueLeft, y, recipeForest$valueWidth(), 20,
                mouseX, mouseY, true, value);
    }

    @Unique
    private void recipeForest$renderKeyRow(GuiGraphics graphics, int mouseX, int mouseY, int row) {
        int y = recipeForest$rowY(row);
        graphics.drawString(font, Component.translatable("screen.emi_recipeforest.settings.forest_key"),
                recipeForest$panelLeft() + 10, y + 6, 0xFFFFFFFF, false);
        Component key = recipeForest$capturingKey
                ? Component.translatable("screen.emi_recipeforest.settings.forest_key.capture")
                : InputConstants.Type.KEYSYM.getOrCreate(ForestBookmarks.getForestKeyCode()).getDisplayName();
        int resetWidth = recipeForest$keyResetWidth();
        recipeForest$renderButton(graphics, recipeForest$valueLeft(), y,
                recipeForest$keyButtonWidth(), 20, mouseX, mouseY, true, key);
        recipeForest$renderButton(graphics, recipeForest$valueLeft() + recipeForest$valueWidth() - resetWidth, y,
                resetWidth, 20, mouseX, mouseY, true,
                Component.translatable("screen.emi_recipeforest.settings.forest_key.reset"));
    }

    @Unique
    private void recipeForest$renderStepperRow(GuiGraphics graphics, int mouseX, int mouseY, int row,
            Component label, boolean canDecrease, boolean canIncrease) {
        int y = recipeForest$rowY(row);
        graphics.drawString(font, label, recipeForest$panelLeft() + 10, y + 6, 0xFFFFFFFF, false);
        recipeForest$renderButton(graphics, recipeForest$panelLeft() + recipeForest$panelWidth() - 50, y,
                20, 20, mouseX, mouseY, canDecrease,
                Component.translatable("screen.emi_recipeforest.settings.columns.decrease"));
        recipeForest$renderButton(graphics, recipeForest$panelLeft() + recipeForest$panelWidth() - 26, y,
                20, 20, mouseX, mouseY, canIncrease,
                Component.translatable("screen.emi_recipeforest.settings.columns.increase"));
    }

    @Unique
    private void recipeForest$renderButton(GuiGraphics graphics, int x, int y, int buttonWidth, int buttonHeight,
            int mouseX, int mouseY, boolean active, Component label) {
        boolean hovered = active && recipeForest$contains(x, y, buttonWidth, buttonHeight, mouseX, mouseY);
        int border = active ? (hovered ? 0xFFFFFFFF : 0xFF8099FF) : 0xFF555555;
        int text = active ? 0xFFFFFFFF : 0xFF777777;
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, border);
        graphics.fill(x + 1, y + 1, x + buttonWidth - 1, y + buttonHeight - 1, 0xFF303030);
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + 6, text);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void recipeForest$handleSettingsClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!recipeForest$settingsOpen) {
            return;
        }
        recipeForest$consumeNextMouseRelease = true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            recipeForest$clickSetting(mouseX, mouseY);
        }
        cir.setReturnValue(true);
    }

    @Unique
    private void recipeForest$clickSetting(double mouseX, double mouseY) {
        int row = recipeForest$rowAt(mouseX, mouseY);
        if (row == 0 && recipeForest$inValue(mouseX, mouseY, row)) {
            ForestBookmarks.setResolutionScope(recipeForest$next(ForestBookmarks.getResolutionScope()));
        } else if (row == 1 && recipeForest$inValue(mouseX, mouseY, row)) {
            ForestBookmarks.setRootLayout(recipeForest$next(ForestBookmarks.getRootLayout()));
        } else if (row == 2 && recipeForest$inValue(mouseX, mouseY, row)) {
            ForestBookmarks.setQuantityMode(recipeForest$next(ForestBookmarks.getQuantityMode()));
        } else if (row == 3) {
            int resetWidth = recipeForest$keyResetWidth();
            int resetLeft = recipeForest$valueLeft() + recipeForest$valueWidth() - resetWidth;
            if (recipeForest$contains(resetLeft, recipeForest$rowY(row), resetWidth, 20, mouseX, mouseY)) {
                ForestBookmarks.setForestKeyCode(ForestBookmarks.DEFAULT_FOREST_KEY_CODE);
                recipeForest$capturingKey = false;
            } else if (recipeForest$contains(recipeForest$valueLeft(), recipeForest$rowY(row),
                    recipeForest$keyButtonWidth(), 20, mouseX, mouseY)) {
                recipeForest$capturingKey = true;
            }
        } else if (row == 4 && recipeForest$inValue(mouseX, mouseY, row)) {
            ForestBookmarks.setBoxEnabled(!ForestBookmarks.isBoxEnabled());
        } else if (row == 5) {
            recipeForest$adjustStepper(mouseX, mouseY,
                    () -> ForestBookmarks.setStacksPerBox(ForestBookmarks.getStacksPerBox() - 1),
                    () -> ForestBookmarks.setStacksPerBox(ForestBookmarks.getStacksPerBox() + 1));
        } else if (ForestBookmarks.getRootLayout() == RootLayout.GRID && row == 6) {
            recipeForest$adjustStepper(mouseX, mouseY,
                    () -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns() - 1,
                            ForestBookmarks.getRootGridRows()),
                    () -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns() + 1,
                            ForestBookmarks.getRootGridRows()));
        } else if (ForestBookmarks.getRootLayout() == RootLayout.GRID && row == 7) {
            recipeForest$adjustStepper(mouseX, mouseY,
                    () -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns(),
                            ForestBookmarks.getRootGridRows() - 1),
                    () -> ForestBookmarks.setRootGridSize(ForestBookmarks.getRootGridColumns(),
                            ForestBookmarks.getRootGridRows() + 1));
        }

        if (recipeForest$contains(recipeForest$doneLeft(),
                recipeForest$panelTop() + recipeForest$panelHeight() - 25,
                recipeForest$doneWidth(), 20, mouseX, mouseY)) {
            recipeForest$settingsOpen = false;
            recipeForest$capturingKey = false;
        }
    }

    @Unique
    private void recipeForest$adjustStepper(double mouseX, double mouseY, Runnable decrease, Runnable increase) {
        int left = recipeForest$panelLeft() + recipeForest$panelWidth();
        int row = recipeForest$rowAt(mouseX, mouseY);
        int y = recipeForest$rowY(row);
        if (recipeForest$contains(left - 50, y, 20, 20, mouseX, mouseY)) {
            decrease.run();
        } else if (recipeForest$contains(left - 26, y, 20, 20, mouseX, mouseY)) {
            increase.run();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void recipeForest$handleSettingsKey(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!recipeForest$settingsOpen) {
            return;
        }
        recipeForest$consumeNextKeyRelease = true;
        if (recipeForest$capturingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                recipeForest$capturingKey = false;
            } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN && !recipeForest$isModifier(keyCode)) {
                ForestBookmarks.setForestKeyCode(keyCode);
                recipeForest$capturingKey = false;
            }
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            recipeForest$settingsOpen = false;
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void recipeForest$consumeSettingsKeyRelease(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (recipeForest$settingsOpen || recipeForest$consumeNextKeyRelease) {
            recipeForest$consumeNextKeyRelease = false;
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (recipeForest$settingsOpen || recipeForest$consumeNextMouseRelease) {
            recipeForest$consumeNextMouseRelease = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (recipeForest$settingsOpen) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (recipeForest$settingsOpen) {
            if (amount != 0) {
                recipeForest$scrollOffset -= (int) Math.signum(amount) * RECIPE_FOREST$ROW_HEIGHT;
                recipeForest$clampScroll();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (recipeForest$settingsOpen) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Unique
    private int recipeForest$rowAt(double mouseX, double mouseY) {
        if (mouseX < recipeForest$panelLeft() || mouseX >= recipeForest$panelLeft() + recipeForest$panelWidth()) {
            return -1;
        }
        if (mouseY < recipeForest$contentTop() || mouseY >= recipeForest$contentBottom()) {
            return -1;
        }
        int row = (int) ((mouseY - recipeForest$contentTop() + recipeForest$scrollOffset)
                / RECIPE_FOREST$ROW_HEIGHT);
        return row <= recipeForest$lastRow() ? row : -1;
    }

    @Unique
    private boolean recipeForest$inValue(double mouseX, double mouseY, int row) {
        return recipeForest$contains(recipeForest$valueLeft(), recipeForest$rowY(row),
                recipeForest$valueWidth(), 20, mouseX, mouseY);
    }

    @Unique
    private int recipeForest$rowY(int row) {
        return recipeForest$contentTop() + row * RECIPE_FOREST$ROW_HEIGHT - recipeForest$scrollOffset;
    }

    @Unique
    private int recipeForest$contentTop() {
        return recipeForest$panelTop() + 27;
    }

    @Unique
    private int recipeForest$contentBottom() {
        return Math.max(recipeForest$contentTop(),
                recipeForest$panelTop() + recipeForest$panelHeight() - 28);
    }

    @Unique
    private int recipeForest$lastRow() {
        return ForestBookmarks.getRootLayout() == RootLayout.GRID ? 7 : 5;
    }

    @Unique
    private void recipeForest$clampScroll() {
        int contentHeight = Math.max(0, recipeForest$contentBottom() - recipeForest$contentTop());
        int rowsHeight = (recipeForest$lastRow() + 1) * RECIPE_FOREST$ROW_HEIGHT;
        recipeForest$scrollOffset = Math.max(0, Math.min(recipeForest$scrollOffset,
                Math.max(0, rowsHeight - contentHeight)));
    }

    @Unique
    private int recipeForest$panelWidth() {
        return Math.max(1, Math.min(RECIPE_FOREST$PANEL_WIDTH, width - 8));
    }

    @Unique
    private int recipeForest$panelHeight() {
        return Math.max(1, Math.min(RECIPE_FOREST$PANEL_HEIGHT, height - 8));
    }

    @Unique
    private int recipeForest$panelLeft() {
        return (width - recipeForest$panelWidth()) / 2;
    }

    @Unique
    private int recipeForest$panelTop() {
        return (height - recipeForest$panelHeight()) / 2;
    }

    @Unique
    private int recipeForest$valueLeft() {
        return recipeForest$panelLeft() + Math.min(135, recipeForest$panelWidth() / 2);
    }

    @Unique
    private int recipeForest$valueWidth() {
        return Math.max(1,
                recipeForest$panelLeft() + recipeForest$panelWidth() - 7 - recipeForest$valueLeft());
    }

    @Unique
    private int recipeForest$keyResetWidth() {
        return Math.max(1, Math.min(48, recipeForest$valueWidth() / 3));
    }

    @Unique
    private int recipeForest$keyButtonWidth() {
        return Math.max(1, recipeForest$valueWidth() - recipeForest$keyResetWidth() - 4);
    }

    @Unique
    private int recipeForest$doneWidth() {
        return Math.max(1, Math.min(150, recipeForest$panelWidth() - 12));
    }

    @Unique
    private int recipeForest$doneLeft() {
        return (width - recipeForest$doneWidth()) / 2;
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
                + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Unique
    private static boolean recipeForest$contains(int x, int y, int width, int height,
            double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
