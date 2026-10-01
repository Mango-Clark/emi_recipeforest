package io.github.mango_clark.emirecipeforest.screen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager.ScreenSpace;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import dev.emi.emi.screen.tooltip.EmiTooltip;
import dev.emi.emi.api.widget.Bounds;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** Addon data displayed through EMI's native sidebar paging, rendering and hit testing. */
public final class ForestSidebar extends SidebarPanel {
    private final boolean roots;
    private final Map<MaterialTree, RootCard> rootCards = new IdentityHashMap<>();

    /**
     * @param roots whether this panel displays live roots instead of saved bookmarks
     */
    public ForestSidebar(boolean roots) {
        super(SidebarSide.LEFT, new SidebarPages(
                List.of(new SidebarPages.SidebarPage(SidebarType.EMPTY)), SidebarSettings.LEFT));
        this.roots = roots;
    }

    /** @return whether this is the live-root panel */
    public boolean isRoots() {
        return roots;
    }

    @Override
    public boolean isSearch() {
        return false;
    }

    @Override
    public boolean supportsType(SidebarType type) {
        return false;
    }

    @Override
    public void setType(SidebarType type) {
    }

    @Override
    public void cycleType(int amount) {
    }

    /** Detaches old batchers before EMI releases its screen-space claims. */
    public void clearSpaces() {
        space = null;
        spaces = List.of();
    }

    /**
     * @param template native grid geometry allocated for this panel
     * @param exclusion native screen exclusions
     */
    public void populate(ScreenSpace template, List<Bounds> exclusion) {
        setSpaces(new CardSpace(template, exclusion, this::cards), List.of());
    }

    private List<? extends EmiIngredient> cards() {
        if (!roots) {
            return ForestBookmarks.cards();
        }
        List<MaterialTree> trees = ForestManager.getTrees();
        rootCards.keySet().removeIf(tree -> !trees.contains(tree));
        List<RootCard> cards = new ArrayList<>(trees.size());
        for (MaterialTree tree : trees) {
            cards.add(rootCards.computeIfAbsent(tree, RootCard::new));
        }
        return cards;
    }

    private static final class CardSpace extends ScreenSpace {
        private final Supplier<List<? extends EmiIngredient>> cards;

        private CardSpace(ScreenSpace template, List<Bounds> exclusion,
                Supplier<List<? extends EmiIngredient>> cards) {
            super(template.tx, template.ty, template.tw, template.th, template.rtl, exclusion,
                    () -> SidebarType.EMPTY, false);
            this.cards = cards;
        }

        @Override
        public List<? extends EmiIngredient> getStacks() {
            return cards.get();
        }
    }

    /** A native recipe favorite retaining the identity of a live forest root. */
    public static final class RootCard extends EmiFavorite {
        private final MaterialTree tree;

        private RootCard(MaterialTree tree) {
            super(tree.goal.recipe.getOutputs().isEmpty() ? EmiStack.EMPTY
                    : tree.goal.recipe.getOutputs().get(0), tree.goal.recipe);
            this.tree = tree;
        }

        /** @return current canonical index, or -1 after this root was removed */
        public int index() {
            List<MaterialTree> trees = ForestManager.getTrees();
            for (int i = 0; i < trees.size(); i++) {
                if (trees.get(i) == tree) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public EmiIngredient copy() {
            return new EmiFavorite(getStack().copy(), getRecipe());
        }

        @Override
        public List<ClientTooltipComponent> getTooltip() {
            List<ClientTooltipComponent> tooltip = new ArrayList<>(super.getTooltip());
            tooltip.addAll(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.root.controls"));
            return tooltip;
        }
    }
}
