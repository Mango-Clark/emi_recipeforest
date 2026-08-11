package io.github.mango_clark.emirecipeforest.screen;

import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Configures the persisted Forest root-grid dimensions. */
public final class ForestSettingsScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.emi_recipeforest.settings.title");
    private static final Component DONE = Component.translatable("screen.emi_recipeforest.settings.done");
    private static final Component DECREASE_COLUMNS =
            Component.translatable("screen.emi_recipeforest.settings.columns.decrease");
    private static final Component INCREASE_COLUMNS =
            Component.translatable("screen.emi_recipeforest.settings.columns.increase");
    private static final Component DECREASE_ROWS =
            Component.translatable("screen.emi_recipeforest.settings.rows.decrease");
    private static final Component INCREASE_ROWS =
            Component.translatable("screen.emi_recipeforest.settings.rows.increase");

    private final Screen parent;
    private Button decreaseColumns;
    private Button increaseColumns;
    private Button decreaseRows;
    private Button increaseRows;

    public ForestSettingsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new ForestSettingsScreen(parent));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int firstRowY = height / 2 - 32;
        decreaseColumns = addRenderableWidget(Button.builder(DECREASE_COLUMNS, button -> adjustColumns(-1))
                .bounds(centerX - 110, firstRowY, 20, 20)
                .build());
        increaseColumns = addRenderableWidget(Button.builder(INCREASE_COLUMNS, button -> adjustColumns(1))
                .bounds(centerX + 90, firstRowY, 20, 20)
                .build());
        decreaseRows = addRenderableWidget(Button.builder(DECREASE_ROWS, button -> adjustRows(-1))
                .bounds(centerX - 110, firstRowY + 34, 20, 20)
                .build());
        increaseRows = addRenderableWidget(Button.builder(INCREASE_ROWS, button -> adjustRows(1))
                .bounds(centerX + 90, firstRowY + 34, 20, 20)
                .build());
        addRenderableWidget(Button.builder(DONE, button -> onClose())
                .bounds(centerX - 75, firstRowY + 76, 150, 20)
                .build());
        updateButtonStates();
    }

    private void adjustColumns(int amount) {
        ForestBookmarks.setRootGridSize(
                ForestBookmarks.getRootGridColumns() + amount, ForestBookmarks.getRootGridRows());
        updateButtonStates();
    }

    private void adjustRows(int amount) {
        ForestBookmarks.setRootGridSize(
                ForestBookmarks.getRootGridColumns(), ForestBookmarks.getRootGridRows() + amount);
        updateButtonStates();
    }

    private void updateButtonStates() {
        int columns = ForestBookmarks.getRootGridColumns();
        int rows = ForestBookmarks.getRootGridRows();
        decreaseColumns.active = columns > 1;
        increaseColumns.active = columns < 16;
        decreaseRows.active = rows > 1;
        increaseRows.active = rows < 8;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = width / 2;
        int firstRowY = height / 2 - 32;
        graphics.drawCenteredString(font, title, centerX, firstRowY - 34, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.emi_recipeforest.settings.columns",
                        ForestBookmarks.getRootGridColumns()),
                centerX, firstRowY + 6, 0xFFFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.emi_recipeforest.settings.rows", ForestBookmarks.getRootGridRows()),
                centerX, firstRowY + 40, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
