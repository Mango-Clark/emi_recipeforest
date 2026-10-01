package io.github.mango_clark.emirecipeforest.mixin;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.widget.SizedButtonWidget;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.SearchBookmark;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.TreeBookmark;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import io.github.mango_clark.emirecipeforest.input.ForestBind;
import io.github.mango_clark.emirecipeforest.screen.BookmarkNameScreen;
import io.github.mango_clark.emirecipeforest.screen.ForestScreen;
import io.github.mango_clark.emirecipeforest.screen.ForestSidebar;
import io.github.mango_clark.emirecipeforest.screen.ForestSidebar.RootCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {
    @Unique
    private static final ResourceLocation RECIPE_FOREST$BUTTONS = EmiPort.id("emi_recipeforest",
            "textures/gui/buttons.png");

    @Shadow
    private static EmiPlayerInventory lastPlayerInventory;

    @Shadow
    private static List<SidebarPanel> panels;
    @Shadow private static List<Bounds> lastExclusion;

    @Unique private static ForestSidebar recipeForest$saved;
    @Unique private static ForestSidebar recipeForest$roots;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void recipeForest$replaceTreeButtonCallback(CallbackInfo ci) {
        recipeForest$saved = new ForestSidebar(false);
        recipeForest$roots = new ForestSidebar(true);
        panels = new ArrayList<>(panels);
        panels.add(recipeForest$saved);
        panels.add(recipeForest$roots);
        EmiScreenManager.tree = new RecipeForestTreeButton(0, 0, button -> {
            if (ForestManager.isEmpty()) {
                EmiApi.viewRecipeTree();
            } else {
                ForestScreen.open();
            }
        });
    }

    @Inject(method = "recalculate", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/StackBatcher$ClaimedCollection;unclaimAll()V"))
    private static void recipeForest$detachOldSpaces(CallbackInfo ci) {
        recipeForest$saved.clearSpaces();
        recipeForest$roots.clearSpaces();
    }

    @Inject(method = "recalculate", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/screen/EmiScreenManager;updateSidebarButtons()V"))
    private static void recipeForest$allocatePanels(CallbackInfo ci) {
        SidebarPanel host = null;
        for (int i = 0; i < 4; i++) {
            SidebarPanel candidate = panels.get(i);
            if (candidate.isVisible() && candidate.getType() != SidebarType.CHESS
                    && candidate.supportsType(SidebarType.FAVORITES)) {
                host = candidate;
                break;
            }
        }
        if (host == null || host.space == null) {
            return;
        }
        ScreenSpace main = host.space;
        int padding = host.theme.verticalPadding;
        int rows = main.th >= 14 ? 2 : 1;
        int height = 18 + rows * 18 + padding * 2 + 3;
        int reservedRows = (height * 2 + 17) / 18;
        if (main.th < reservedRows + 2) {
            return;
        }
        // EMI has already constrained all four sidebars against the full host envelope.
        // Partition that envelope only after layout; no other sidebar can overlap the additions.
        List<Bounds> exclusion = lastExclusion;
        List<ScreenSpace> oldSpaces = host.getSpaces();
        List<ScreenSpace> subspaces = new ArrayList<>();
        for (int i = 1; i < oldSpaces.size(); i++) {
            ScreenSpace old = oldSpaces.get(i);
            subspaces.add(new ScreenSpace(old.tx, old.ty - reservedRows * 18, old.tw, old.th,
                    old.rtl, exclusion, old::getType, old.search));
        }
        ScreenSpace shortened = new ScreenSpace(main.tx, main.ty, main.tw, main.th - reservedRows,
                main.rtl, exclusion, host::getType, host.isSearch());
        host.setSpaces(shortened, subspaces);
        ScreenSpace end = host.getSpaces().get(host.getSpaces().size() - 1);
        int top = end.ty + end.th * 18 + padding * 2 + 3 + 18;
        recipeForest$positionPanel(recipeForest$saved, main, top, rows, host.theme, exclusion);
        recipeForest$positionPanel(recipeForest$roots, main, top + height, rows, host.theme, exclusion);
    }

    @Unique
    private static void recipeForest$positionPanel(ForestSidebar panel, ScreenSpace host, int y, int rows,
            SidebarTheme theme, List<Bounds> exclusion) {
        panel.theme = theme;
        panel.header = true;
        panel.populate(new ScreenSpace(host.tx, y, host.tw, rows, host.rtl, exclusion,
                () -> SidebarType.EMPTY, false), exclusion);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$addHoveredRecipeToForest(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ForestBind.INSTANCE.matchesKey(keyCode, scanCode) || EmiApi.getHandledScreen() == null
                || recipeForest$hasFocusedTextField()) {
            return;
        }

        if (recipeForest$addHoveredRecipe(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$addHoveredRecipeToForest(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ForestBind.INSTANCE.matchesMouse(button) || EmiApi.getHandledScreen() == null
                || recipeForest$hasFocusedTextField()) {
            return;
        }
        if (recipeForest$addHoveredRecipe((int) mouseX, (int) mouseY)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean recipeForest$addHoveredRecipe(int mouseX, int mouseY) {
        EmiStackInteraction hovered = EmiScreenManager.getHoveredStack(
                mouseX, mouseY, true);
        if (hovered == null || hovered.isEmpty()) {
            return false;
        }
        if (hovered.getStack() instanceof RootCard root) {
            int index = root.index();
            if (index >= 0) {
                ForestManager.remove(index);
                recipeForest$refreshPanels();
            }
            return true;
        }
        EmiRecipe recipe = recipeForest$resolveRecipe(hovered);
        if (recipe == null || !recipe.supportsRecipeTree()) {
            return false;
        }

        ForestManager.add(recipe);
        recipeForest$refreshPanels();
        return true;
    }

    @Inject(method = "stackInteraction", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$handleViewStackForest(EmiStackInteraction hovered,
            Function<EmiBind, Boolean> input, CallbackInfoReturnable<Boolean> cir) {
        if (!input.apply(EmiConfig.viewStackTree)) {
            return;
        }
        EmiRecipe recipe = recipeForest$resolveRecipe(hovered);
        if (recipe != null && recipe.supportsRecipeTree()) {
            ForestManager.add(recipe);
            ForestScreen.open();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static EmiRecipe recipeForest$resolveRecipe(EmiStackInteraction hovered) {
        EmiRecipe recipe = hovered.getRecipeContext();
        if ((recipe == null || !recipe.supportsRecipeTree()) && lastPlayerInventory != null) {
            LinkedHashSet<EmiRecipe> candidates = new LinkedHashSet<>();
            for (EmiStack stack : hovered.getStack().getEmiStacks()) {
                candidates.addAll(EmiApi.getRecipeManager().getRecipesByOutput(stack));
            }
            recipe = EmiUtil.getPreferredRecipe(List.copyOf(candidates), lastPlayerInventory, false);
        }
        return recipe;
    }

    @Redirect(method = "genericInteraction", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/api/EmiApi;viewRecipeTree()V"))
    private static void recipeForest$openForestForViewTreeKey() {
        ForestScreen.open();
    }

    @Redirect(method = "stackInteraction", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/bom/BoM;setGoal(Ldev/emi/emi/api/recipe/EmiRecipe;)V"))
    private static void recipeForest$addViewStackTreeGoal(EmiRecipe recipe) {
        ForestManager.add(recipe);
    }

    @Redirect(method = "stackInteraction", at = @At(value = "INVOKE",
            target = "Ldev/emi/emi/api/EmiApi;viewRecipeTree()V"))
    private static void recipeForest$openViewStackTreeForest() {
        ForestScreen.open();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private static void recipeForest$handleBookmarkCard(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        EmiIngredient card = EmiScreenManager.pressedStack;
        EmiIngredient dragged = EmiScreenManager.draggedStack;
        if (!recipeForest$isOwnedCard(card)) {
            if (recipeForest$isOwnedCard(dragged)) {
                recipeForest$clearDragState();
                cir.setReturnValue(true);
            }
            return;
        }

        try {
            if (!dragged.isEmpty()) {
                SidebarPanel target = EmiScreenManager.getHoveredPanel((int) mouseX, (int) mouseY);
                if (target instanceof ForestSidebar forest && forest.space != null
                        && forest.getHoveredSpace((int) mouseX, (int) mouseY) == forest.space) {
                    int edge = forest.space.getClosestEdge((int) mouseX, (int) mouseY)
                            + forest.page * forest.space.pageSize;
                    if (dragged instanceof RootCard root && forest.isRoots()) {
                        int from = root.index();
                        if (from >= 0) {
                            int destination = edge > from ? edge - 1 : edge;
                            ForestManager.move(from, Math.max(0, Math.min(destination, ForestManager.size() - 1)));
                        }
                    } else if (recipeForest$isBookmarkCard(dragged) && !forest.isRoots()) {
                        ForestBookmarks.move(dragged, edge);
                    }
                    recipeForest$refreshPanels();
                }
                return;
            }
            if (EmiScreenManager.getHoveredStack((int) mouseX, (int) mouseY, true).getStack() != card) {
                return;
            }
            if (card instanceof RootCard root) {
                int index = root.index();
                if (index >= 0 && button == 1) {
                    ForestManager.remove(index);
                    recipeForest$refreshPanels();
                } else if (index >= 0 && button == 0) {
                    ForestManager.select(index);
                    ForestScreen.open();
                }
                return;
            }
            if (button == 1) {
                if (card instanceof TreeBookmark tree && EmiInput.isShiftDown()) {
                    BookmarkNameScreen.openForRename(tree);
                } else if (ForestBookmarks.remove(card)) {
                    recipeForest$refreshPanels();
                }
            } else if (button == 0) {
                boolean applied = ForestBookmarks.apply(card);
                if (card instanceof TreeBookmark) {
                    if (applied) {
                        ForestScreen.open();
                    } else if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(
                                Component.translatable("message.emi_recipeforest.bookmark.invalid"), false);
                    }
                }
            }
        } finally {
            recipeForest$clearDragState();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderDraggedStack", at = @At("HEAD"))
    private static void recipeForest$drawInsertionEdge(EmiDrawContext context, int mouseX, int mouseY,
            float delta, EmiScreenBase base, CallbackInfo ci) {
        EmiIngredient dragged = EmiScreenManager.draggedStack;
        if (!recipeForest$isOwnedCard(dragged)) {
            return;
        }
        SidebarPanel panel = EmiScreenManager.getHoveredPanel(mouseX, mouseY);
        if (panel instanceof ForestSidebar forest && forest.space != null
                && forest.getHoveredSpace(mouseX, mouseY) == forest.space
                && (dragged instanceof RootCard) == forest.isRoots()) {
            ScreenSpace space = forest.space;
            int count = space.getStacks().size();
            int start = forest.page * space.pageSize;
            int edge = Math.min(space.getClosestEdge(mouseX, mouseY), Math.max(0, count - start));
            context.push();
            context.matrices().translate(0, 0, 200);
            context.fill(space.getEdgeX(edge) - 1, space.getEdgeY(edge), 2, 18, 0xFF00FFFF);
            context.pop();
        }
    }

    @Unique
    private static boolean recipeForest$isOwnedCard(EmiIngredient ingredient) {
        return recipeForest$isBookmarkCard(ingredient) || ingredient instanceof RootCard;
    }

    @Unique
    private static void recipeForest$refreshPanels() {
        EmiScreenManager.repopulatePanels(SidebarType.EMPTY);
    }

    @Unique
    private static boolean recipeForest$isBookmarkCard(EmiIngredient ingredient) {
        return ingredient instanceof SearchBookmark || ingredient instanceof TreeBookmark;
    }

    @Unique
    private static boolean recipeForest$hasFocusedTextField() {
        if (EmiScreenManager.search != null && EmiScreenManager.search.canConsumeInput()) {
            return true;
        }
        return Minecraft.getInstance().screen instanceof ContainerEventHandler handler
                && recipeForest$hasFocusedTextField(handler, 10);
    }

    @Unique
    private static boolean recipeForest$hasFocusedTextField(ContainerEventHandler parent, int depthRemaining) {
        if (depthRemaining <= 0) {
            return false;
        }
        for (GuiEventListener child : parent.children()) {
            if (child instanceof EditBox field && field.visible && field.canConsumeInput()) {
                return true;
            }
            if (child instanceof ContainerEventHandler nested
                    && recipeForest$hasFocusedTextField(nested, depthRemaining - 1)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void recipeForest$clearDragState() {
        EmiScreenManager.pressedStack = EmiStack.EMPTY;
        EmiScreenManager.draggedStack = EmiStack.EMPTY;
    }

    @Unique
    private static final class RecipeForestTreeButton extends SizedButtonWidget {
        private RecipeForestTreeButton(int x, int y, net.minecraft.client.gui.components.Button.OnPress action) {
            super(x, y, 20, 20, 184, 0, () -> true, action,
                    List.of(
                            Component.translatable("tooltip.emi_recipeforest.recipe_tree"),
                            Component.translatable("tooltip.emi_recipeforest.recipe_tree.description")));
            texture = RECIPE_FOREST$BUTTONS;
        }
    }
}
