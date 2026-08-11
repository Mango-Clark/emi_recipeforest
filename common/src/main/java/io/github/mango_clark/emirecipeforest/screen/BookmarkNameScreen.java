package io.github.mango_clark.emirecipeforest.screen;

import java.util.Objects;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.TreeBookmark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small callback-based name prompt used by forest bookmark actions. */
public final class BookmarkNameScreen extends Screen {
    private static final Component TITLE = Component.translatable("screen.emi_recipeforest.bookmark_name.title");
    private static final Component FIELD = Component.translatable("screen.emi_recipeforest.bookmark_name.field");
    private static final Component CONFIRM = Component.translatable("screen.emi_recipeforest.bookmark_name.confirm");
    private static final Component CANCEL = Component.translatable("screen.emi_recipeforest.bookmark_name.cancel");

    private final Screen parent;
    private final Consumer<String> callback;
    private final String initialName;
    private EditBox nameBox;
    private Button confirmButton;

    public BookmarkNameScreen(Screen parent, String initialName, Consumer<String> callback) {
        super(TITLE);
        this.parent = parent;
        this.initialName = initialName == null ? "" : initialName;
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    public static void openForSave() {
        Minecraft client = Minecraft.getInstance();
        openForSave(client.screen);
    }

    public static void openForSave(Screen parent) {
        Minecraft.getInstance().setScreen(new BookmarkNameScreen(parent, "",
            name -> ForestBookmarks.addTree(name)));
    }

    public static void openForRename(TreeBookmark bookmark) {
        Minecraft client = Minecraft.getInstance();
        openForRename(client.screen, bookmark);
    }

    public static void openForRename(Screen parent, TreeBookmark bookmark) {
        Objects.requireNonNull(bookmark, "bookmark");
        Minecraft.getInstance().setScreen(new BookmarkNameScreen(parent, bookmark.name(),
            name -> ForestBookmarks.rename(bookmark, name)));
    }

    @Override
    protected void init() {
        String currentName = nameBox == null ? initialName : nameBox.getValue();
        int centerX = width / 2;
        int fieldY = height / 2 - 24;

        nameBox = addRenderableWidget(new EditBox(font, centerX - 100, fieldY, 200, 20, FIELD));
        nameBox.setMaxLength(80);
        nameBox.setValue(currentName);
        nameBox.setHint(FIELD);
        nameBox.setResponder(value -> updateConfirmState());

        confirmButton = addRenderableWidget(Button.builder(CONFIRM, button -> submit())
            .bounds(centerX - 102, fieldY + 32, 100, 20)
            .build());
        addRenderableWidget(Button.builder(CANCEL, button -> onClose())
            .bounds(centerX + 2, fieldY + 32, 100, 20)
            .build());

        setInitialFocus(nameBox);
        nameBox.setFocused(true);
        updateConfirmState();
    }

    private void updateConfirmState() {
        if (confirmButton != null && nameBox != null) {
            confirmButton.active = !nameBox.getValue().strip().isEmpty();
        }
    }

    private void submit() {
        String name = nameBox.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        callback.accept(name);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && confirmButton.active) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFFFFF);
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
