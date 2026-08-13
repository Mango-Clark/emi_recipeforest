package io.github.mango_clark.emirecipeforest.screen;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;

import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.ChanceMaterialCost;
import dev.emi.emi.bom.ChanceState;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.FoldState;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.bom.ProgressState;
import dev.emi.emi.bom.TreeCost;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.data.EmiRecipeCategoryProperties;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.registry.EmiStackList;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiHistory;
import dev.emi.emi.screen.BoMScreen;
import dev.emi.emi.screen.StackBatcher;
import dev.emi.emi.screen.StackBatcher.Batchable;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.tooltip.EmiTooltip;
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks;
import io.github.mango_clark.emirecipeforest.bookmark.ForestBookmarks.ResolutionScope;
import io.github.mango_clark.emirecipeforest.compat.EmiCompatibility;
import io.github.mango_clark.emirecipeforest.forest.ForestCosts;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import io.github.mango_clark.emirecipeforest.forest.QuantityDisplay;
import io.github.mango_clark.emirecipeforest.forest.QuantityDisplay.DisplayMode;

/** Multi-root replacement for EMI's recipe tree screen. */
public class ForestScreen extends BoMScreen {
	private static final int NODE_WIDTH = 30;
	private static final int NODE_HORIZONTAL_SPACING = 8;
	private static final int NODE_VERTICAL_SPACING = 20;
	private static final int COST_HORIZONTAL_SPACING = 8;
	private static final int ROOT_CELL_SIZE = 20;
	private static final int ROOT_PANEL_MARGIN = 4;
	private static final int ROOT_PANEL_HEADER_HEIGHT = 26;
	private static final int ROOT_PANEL_FOOTER_HEIGHT = 22;
	private static final int ROOT_LIST_ROW_HEIGHT = 24;
	private static final int ROOT_LIST_CONTROL_WIDTH = 12;
	private static final int ROOT_LIST_CONTROL_COUNT = 5;
	private static final int ROOT_LIST_STACKED_CONTROL_HEIGHT = 9;
	private static final int ROOT_LIST_CONTROL_GAP = 2;
	private static final int ROOT_LIST_RIGHT_MARGIN = 3;
	private static final int ROOT_LIST_SCROLLBAR_WIDTH = 4;
	private static final ResourceLocation RECIPE_FOREST_WIDGETS = EmiPort.id("emi_recipeforest",
		"textures/gui/widgets.png");
	private static final ThreadLocal<Boolean> OPENING_FOREST = ThreadLocal.withInitial(() -> false);
	private static StackBatcher batcher = new StackBatcher();
	private static int zoom = 0;
	private Bounds batches = new Bounds(-24, -50, 48, 26);
	private Bounds mode = new Bounds(-24, -50, 16, 16);
	private Bounds help = new Bounds(0, 0, 16, 16);
	private Bounds previousPage = Bounds.EMPTY;
	private Bounds nextPage = Bounds.EMPTY;
	private Bounds rootLayoutToggle = Bounds.EMPTY;
	private double offX, offY;
	private List<Node> nodes = Lists.newArrayList();
	private List<Cost> costs = Lists.newArrayList();
	private EmiPlayerInventory playerInv;
	private boolean hasRemainders = false;;
	private int page;
	private int rootListScroll;
	private int nodeWidth = 0;
	private int nodeHeight = 0;
	private int lastMouseX, lastMouseY;
	private double scrollAcc = 0;
	private double rootBatchScrollAcc = 0;
	private int rootBatchScrollIndex = -1;
	private boolean rootPanelDrag;
	private boolean rootListScrollbarDrag;
	private boolean rootGridScrollbarDrag;
	private boolean altLayout;
	private MaterialTree lastCalculatedTree;
	private boolean lastCalculatedForestEmpty;

    /**
     * Creates a forest screen with EMI's handled-screen return context.
     *
     * @param old handled screen restored on close
     */
    public ForestScreen(AbstractContainerScreen<?> old) {
		super(old);
	}

	/** Opens the forest through EMI's BoM screen factory while preserving its handled-screen context. */
	public static void open() {
		OPENING_FOREST.set(true);
		try {
			EmiApi.viewRecipeTree();
		} finally {
			OPENING_FOREST.remove();
		}
	}

	/**
	 * Reports the synchronous factory override requested by {@link #open()}.
	 *
	 * @return whether EMI should construct a forest screen
	 */
	public static boolean isForestOpenRequested() {
		return OPENING_FOREST.get();
	}

	public void init() {
		ForestManager.cancelPendingResolution();
		altLayout = EmiInput.isAltDown();
		if (ForestManager.getSelectedTree() != null) {
			offY = height / -3;
		} else {
			offY = 0;
		}
		recalculateTree();
	}

	public void recalculateTree() {
		MaterialTree selectedTree = ForestManager.getSelectedTree();
		boolean forestEmpty = ForestManager.isEmpty();
		help = new Bounds(Math.max(2, rootPanelLeft() - 18), height - 18, 16, 16);
		int pageCount = Math.max(1, (ForestManager.size() + rootsPerPage() - 1) / rootsPerPage());
		page = Mth.clamp(page, 0, pageCount - 1);
		if (rootPanelHasContent()) {
			int panelLeft = rootPanelLeft();
			int panelBottom = rootPanelTop() + rootPanelHeight();
			previousPage = new Bounds(panelLeft + 4, panelBottom - 19, 16, 16);
			nextPage = new Bounds(panelLeft + rootPanelWidth() - 20, panelBottom - 19, 16, 16);
			rootLayoutToggle = new Bounds(panelLeft + 24, rootPanelTop() + 4,
				Math.max(0, rootPanelWidth() - 28), Math.min(18, Math.max(0, rootPanelHeight() - 8)));
		} else {
			previousPage = Bounds.EMPTY;
			nextPage = Bounds.EMPTY;
			rootLayoutToggle = Bounds.EMPTY;
		}
		clampRootListScroll();
		if (selectedTree != null) {
			TreeVolume volume = forest$addNewNodes(selectedTree.goal, selectedTree.batches, 1, 0, ChanceState.DEFAULT);
			nodes = volume.nodes;
			int horizontalOffset = (volume.getMaxRight() + volume.getMinLeft()) / 2;
			for (Node node : volume.nodes) {
				node.x -= horizontalOffset;
			}
			if (!volume.nodes.isEmpty()) {
				Node node = volume.nodes.get(0);
				int width = font.width("x" + selectedTree.batches);
				batches = new Bounds(node.x + node.width / 2 + 6, node.y - 10, width + 12, 22);
			}

			nodeWidth = volume.getMaxRight() - volume.getMinLeft();
			nodeHeight = getNodeHeight(selectedTree.goal);
			playerInv = minecraft.player == null ? null : EmiPlayerInventory.of(minecraft.player);
			ForestCosts forestCosts = ForestCosts.calculateNew(ForestManager.getTrees(), playerInv);
			TreeCost progressTreeCost = forestCosts.getProgress();
			TreeCost totalTreeCost = forestCosts.getTotal();
			Map<EmiIngredient, FlatMaterialCost> progressCosts = progressTreeCost.costs.values().stream()
				.collect(Collectors.toMap(c -> c.ingredient, c -> c));
			Map<EmiIngredient, ChanceMaterialCost> chanceProgressCosts = progressTreeCost.chanceCosts.values().stream()
				.collect(Collectors.toMap(c -> c.ingredient, c -> c));

			costs.clear();

			List<FlatMaterialCost> treeCosts = Stream.concat(
				totalTreeCost.costs.values().stream(),
				totalTreeCost.chanceCosts.values().stream()
			).sorted((a, b) -> Integer.compare(
				EmiStackList.getIndex(a.ingredient.getEmiStacks().get(0)),
				EmiStackList.getIndex(b.ingredient.getEmiStacks().get(0))
			)).toList();
			int cy = nodeHeight * NODE_VERTICAL_SPACING * 2;
			int costX = 0;
			for (FlatMaterialCost node : treeCosts) {
				Cost cost = new Cost(node, costX, cy, false);
				if (BoM.craftingMode) {
					if (node instanceof ChanceMaterialCost cmc) {
						if (!chanceProgressCosts.containsKey(node.ingredient)) {
							cost.alreadyDone = node.getEffectiveAmount();
						} else {
							ChanceMaterialCost progress = chanceProgressCosts.get(node.ingredient);
							cost.alreadyDone = (long) Math.ceil(cmc.amount * cmc.chance - progress.amount * progress.chance);
						}
					} else {
						if (!progressCosts.containsKey(node.ingredient)) {
							cost.alreadyDone = node.amount;
						} else {
							FlatMaterialCost progress = progressCosts.get(node.ingredient);
							cost.alreadyDone = node.amount - progress.amount;
						}
					}
				}
				costs.add(cost);
				costX += 16 + COST_HORIZONTAL_SPACING + cost.getReservedAmountOverflow();
			}
			int costOffset = (costX - COST_HORIZONTAL_SPACING) / 2;
			for (Cost cost : costs) {
				cost.x -= costOffset;
			}

			int totalCostWidth = font.width(EmiPort.translatable("emi.total_cost"));
			mode = new Bounds(totalCostWidth / 2 + 4, cy - 20, 16, 16);

			List<Cost> remainders = Lists.newArrayList();

			List<FlatMaterialCost> remainderCosts = Stream.concat(
				totalTreeCost.remainders.values().stream(),
				totalTreeCost.chanceRemainders.values().stream()
			).sorted((a, b) -> Integer.compare(
				EmiStackList.getIndex(a.ingredient.getEmiStacks().get(0)),
				EmiStackList.getIndex(b.ingredient.getEmiStacks().get(0))
			)).toList();
			cy += 40;
			int remainderX = 0;
			for (FlatMaterialCost node : remainderCosts) {
				if (node.getEffectiveAmount() <= 0) {
					continue;
				}
				Cost cost = new Cost(node, remainderX, cy, true);
				remainders.add(cost);
				remainderX += 16 + COST_HORIZONTAL_SPACING + cost.getReservedAmountOverflow();
			}
			costOffset = (remainderX - COST_HORIZONTAL_SPACING) / 2;
			for (Cost cost : remainders) {
				cost.x -= costOffset;
			}
			costs.addAll(remainders);
			hasRemainders = !remainders.isEmpty();
		} else {
			nodes = Lists.newArrayList();
			costs = Lists.newArrayList();
			hasRemainders = false;
			nodeWidth = 0;
			nodeHeight = 0;
			playerInv = null;
		}
		batcher.repopulate();
		lastCalculatedTree = selectedTree;
		lastCalculatedForestEmpty = forestEmpty;
	}

	private int rootsPerPage() {
		return rootColumns() * rootRows();
	}

	private int rootColumns() {
		int available = Math.max(1, (rootPanelWidth() - 8) / ROOT_CELL_SIZE);
		return Math.max(1, Math.min(ForestBookmarks.getRootGridColumns(), available));
	}

	private int rootRows() {
		int available = Math.max(1,
			(rootPanelHeight() - ROOT_PANEL_HEADER_HEIGHT - ROOT_PANEL_FOOTER_HEIGHT) / ROOT_CELL_SIZE);
		return Math.max(1, Math.min(ForestBookmarks.getRootGridRows(), available));
	}

	private int rootPanelWidth() {
		int available = Math.max(0, width - ROOT_PANEL_MARGIN * 2);
		return Math.min(available, Math.min(192, Math.max(100, width / 3)));
	}

	private int rootPanelLeft() {
		return Math.max(0, width - rootPanelWidth() - ROOT_PANEL_MARGIN);
	}

	private int rootPanelTop() {
		return Math.min(ROOT_PANEL_MARGIN, Math.max(0, height - rootPanelHeight()));
	}

	private int rootPanelHeight() {
		return Math.max(0, height - ROOT_PANEL_MARGIN * 2);
	}

	private boolean rootPanelHasContent() {
		return rootPanelWidth() >= ROOT_LIST_CONTROL_WIDTH * ROOT_LIST_CONTROL_COUNT + 8
			&& rootPanelHeight() > ROOT_PANEL_HEADER_HEIGHT + ROOT_PANEL_FOOTER_HEIGHT;
	}

	private int contentCenterX() {
		return rootPanelLeft() / 2;
	}

	private int contentCenterY() {
		return height / 2;
	}

	private boolean rootPanelContains(double mouseX, double mouseY) {
		return mouseX >= rootPanelLeft() && mouseX < rootPanelLeft() + rootPanelWidth()
			&& mouseY >= rootPanelTop() && mouseY < rootPanelTop() + rootPanelHeight();
	}

	private int rootListTop() {
		return rootPanelTop() + ROOT_PANEL_HEADER_HEIGHT;
	}

	private int rootListBottom() {
		return rootPanelTop() + rootPanelHeight() - ROOT_PANEL_FOOTER_HEIGHT;
	}

	private int rootListMaxScroll() {
		return Math.max(0, ForestManager.size() * ROOT_LIST_ROW_HEIGHT
			- Math.max(0, rootListBottom() - rootListTop()));
	}

	private void clampRootListScroll() {
		rootListScroll = Mth.clamp(rootListScroll, 0, rootListMaxScroll());
	}

	@Override
	public void render(GuiGraphics raw, int mouseX, int mouseY, float delta) {
		MaterialTree selectedTree = ForestManager.getSelectedTree();
		boolean forestEmpty = ForestManager.isEmpty();
		boolean needsRecalculation = lastCalculatedTree != selectedTree
			|| lastCalculatedForestEmpty != forestEmpty;
		boolean currentAltLayout = EmiInput.isAltDown();
		if (altLayout != currentAltLayout) {
			altLayout = currentAltLayout;
			needsRecalculation = true;
		}
		if (needsRecalculation) {
			recalculateTree();
		}
		EmiDrawContext context = EmiDrawContext.wrap(raw);
		context.fill(0, 0, width, height, 0xDD000000);
		this.renderMenuBackground(context.raw());
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		float scale = getScale();
		int scaledWidth = (int) (Math.max(1, rootPanelLeft()) / scale);
		int scaledHeight = (int) (height / scale);
		// TODO should be the ingredient width if higher
		int contentWidth = nodeWidth * NODE_WIDTH;
		int contentHeight = nodeHeight * NODE_VERTICAL_SPACING + 80;
		int xBound = scaledWidth / 2 + contentWidth - 100;
		int topBound = scaledHeight * 1 / -2 + 20;
		int bottomBound = contentHeight + scaledHeight / 2 - 20;
		offX = Mth.clamp(offX, -xBound, xBound);
		offY = Mth.clamp(offY, -bottomBound, -topBound);

		boolean panelHovered = rootPanelContains(mouseX, mouseY);
		int mx = panelHovered ? Integer.MIN_VALUE
			: (int) ((mouseX - contentCenterX()) / scale - offX);
		int my = panelHovered ? Integer.MIN_VALUE
			: (int) ((mouseY - contentCenterY()) / scale - offY);

		Matrix4fStack view = RenderSystem.getModelViewStack();
		view.pushMatrix();
		view.translate(contentCenterX(), contentCenterY(), 0);
		view.scale(scale, scale, 1);
		view.translate((float)offX, (float)offY, 0);
		RenderSystem.applyModelViewMatrix();
		if (BoM.tree != null) {
			batcher.begin(0, 0, 0);
			int cy = nodeHeight * NODE_VERTICAL_SPACING * 2;
			context.drawCenteredText(EmiPort.translatable("emi.total_cost"), 0, cy - 16);
			if (hasRemainders) {
				context.drawCenteredText(EmiPort.translatable("emi.leftovers"), 0, cy - 16 + 40);
			}
			for (Cost cost : costs) {
				cost.renderIcon(context);
			}
			for (Node node : nodes) {
				node.render(context, mx, my, delta);
			}
			int color = -1;
			if (batches.contains(mx, my)) {
				color = 0xff8099ff;
			}
			context.drawTextWithShadow(EmiPort.literal("x" + BoM.tree.batches),
					batches.x() + 6, batches.y() + batches.height() / 2 - 4, color);

			if (mode.contains(mx, my)) {
				context.setColor(0.5f, 0.6f, 1f, 1f);
			}
			context.drawTexture(EmiRenderHelper.WIDGETS, mode.x(), mode.y(), BoM.craftingMode ? 16 : 0, 146, mode.width(), mode.height());
			context.setColor(1f, 1f, 1f, 1f);
			batcher.draw();
			// Amounts deliberately render after the icon batch so they can never be obscured.
			for (Cost cost : costs) {
				cost.renderAmount(context);
			}
			for (Node node : nodes) {
				node.renderAmount(context);
			}
		} else {
			context.drawCenteredText(EmiPort.translatable("emi.tree_welcome", EmiRenderHelper.getEmiText()), 0, -72);
			context.drawCenteredText(EmiPort.translatable("emi.no_tree"), 0, -48);
			context.drawCenteredText(EmiPort.translatable("emi.random_tree"), 0, -24);
			context.drawCenteredText(EmiPort.translatable("emi.random_tree_input"), 0, 0);
		}

		view.popMatrix();
		RenderSystem.applyModelViewMatrix();
		context.push();
		context.matrices().translate(0, 0, 500);
		renderRootPanel(context, mouseX, mouseY, delta);
		context.pop();

		if (help.contains(mouseX, mouseY)) {
			context.setColor(0.5f, 0.6f, 1f, 1f);
		}
		context.drawTexture(EmiRenderHelper.WIDGETS, help.x(), help.y(), 0, 200, help.width(), help.height());
		context.setColor(1f, 1f, 1f, 1f);

		context.push();
		context.matrices().translate(0, 0, 1000);
		Hover hover = forest$getHoveredStack(mouseX, mouseY);
		if (hover != null) {
			hover.drawTooltip(this, context, mouseX, mouseY);
		} else if (!panelHovered && BoM.tree != null && batches.contains(mx, my)) {
			List<ClientTooltipComponent> list = Lists.newArrayList();
			list.addAll(EmiTooltip.splitTranslate("tooltip.emi.bom.batch_size", BoM.tree.batches));
			list.addAll(EmiTooltip.splitTranslate("tooltip.emi.bom.batch_size.ideal", EmiPort.literal("Left Click")));
			EmiRenderHelper.drawTooltip(this, context, list, mouseX, mouseY);
		} else if (!panelHovered && BoM.tree != null && mode.contains(mx, my)) {
			String key = BoM.craftingMode ? "tooltip.emi.bom.mode.craft" : "tooltip.emi.bom.mode.view";
			List<ClientTooltipComponent> list = EmiTooltip.splitTranslate(key, BoM.tree.batches);
			EmiRenderHelper.drawTooltip(this, context, list, mouseX, mouseY);
		} else if (help.contains(mouseX, mouseY)) {
			List<ClientTooltipComponent> list =  EmiTooltip.splitTranslate("tooltip.emi.bom.help");
			EmiRenderHelper.drawTooltip(this, context, list, help.x(), help.y(), width);
		} else if (ForestBookmarks.getRootLayout() == ForestBookmarks.RootLayout.LIST) {
			int direction = rootListMoveDirectionAt(mouseX, mouseY);
			if (direction != 0) {
				String suffix = direction < 0 ? "up" : "down";
				List<ClientTooltipComponent> list = new java.util.ArrayList<>();
				list.addAll(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.move." + suffix));
				list.addAll(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.move.shift." + suffix));
				list.addAll(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.move.control." + suffix));
				list.addAll(EmiTooltip.splitTranslate("tooltip.emi_recipeforest.move.alt." + suffix));
				EmiRenderHelper.drawTooltip(this, context, list, mouseX, mouseY);
			}
		}
		context.pop();
	}

	private Hover forest$getHoveredStack(int mx, int my) {
		if (rootPanelContains(mx, my)) {
			return null;
		}
		float scale = getScale();
		mx = (int) ((mx - contentCenterX()) / scale - offX);
		my = (int) ((my - contentCenterY()) / scale - offY);
		for (Cost cost : costs) {
			if (mx >= cost.x && mx < cost.x + 16 && my >= cost.y && my < cost.y + 16) {
				return new Hover(cost.cost.ingredient);
			}
		}
		for (Node node : nodes) {
			Hover hover = node.getHover(mx, my);
			if (hover != null) {
				return hover;
			}
		}
		return null;
	}

	private void renderRootPanel(EmiDrawContext context, int mouseX, int mouseY, float delta) {
		int panelLeft = rootPanelLeft();
		int panelTop = rootPanelTop();
		context.fill(panelLeft, panelTop, rootPanelWidth(), rootPanelHeight(), 0xB0101010);
		if (!rootPanelHasContent()) {
			return;
		}
		context.drawTexture(RECIPE_FOREST_WIDGETS, panelLeft + 4, panelTop + 5, 0, 0, 16, 16, 16, 64, 32);
		boolean toggleHovered = rootLayoutToggle.contains(mouseX, mouseY);
		context.fill(rootLayoutToggle.x(), rootLayoutToggle.y(), rootLayoutToggle.width(),
			rootLayoutToggle.height(), toggleHovered ? 0xFF8099FF : 0xFF555555);
		context.fill(rootLayoutToggle.x() + 1, rootLayoutToggle.y() + 1,
			rootLayoutToggle.width() - 2, rootLayoutToggle.height() - 2, 0xFF202020);
		String layoutKey = "screen.emi_recipeforest.settings.root_layout."
			+ ForestBookmarks.getRootLayout().name().toLowerCase(java.util.Locale.ROOT);
		context.drawCenteredTextWithShadow(Component.translatable(layoutKey),
			rootLayoutToggle.x() + rootLayoutToggle.width() / 2, rootLayoutToggle.y() + 5, 0xFFFFFFFF);
		if (ForestBookmarks.getRootLayout() == ForestBookmarks.RootLayout.LIST) {
			renderRootList(context, mouseX, mouseY, delta);
			return;
		}

		int columns = rootColumns();
		int left = panelLeft + (rootPanelWidth() - columns * ROOT_CELL_SIZE) / 2;
		int start = page * rootsPerPage();
		int end = Math.min(start + rootsPerPage(), ForestManager.size());
		for (int index = start; index < end; index++) {
			int local = index - start;
			int x = left + local % columns * ROOT_CELL_SIZE;
			int y = panelTop + ROOT_PANEL_HEADER_HEIGHT + local / columns * ROOT_CELL_SIZE;
			boolean selected = index == ForestManager.getSelectedIndex();
			boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
			int border = selected ? 0xFF8099FF : hovered ? 0xFFFFFFFF : 0xFF555555;
			context.fill(x, y, 18, 18, border);
			context.fill(x + 1, y + 1, 16, 16, 0xFF202020);
			MaterialTree tree = ForestManager.getTrees().get(index);
			if (tree != null && tree.goal != null) {
				tree.goal.ingredient.render(context.raw(), x + 1, y + 1, delta,
					~(EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_REMAINDER));
				renderGridBatchAmount(context, x, y, tree.batches);
			}
			context.fill(x + 12, y, 6, 6, 0xFF8C2020);
			context.drawTextWithShadow(EmiPort.literal("×"), x + 12, y - 2, 0xFFFFFFFF);
		}
		if (page > 0) {
			context.drawTextWithShadow(EmiPort.literal("‹"), previousPage.x() + 4, previousPage.y() + 3,
				previousPage.contains(mouseX, mouseY) ? 0xFF8099FF : 0xFFFFFFFF);
		}
		if (end < ForestManager.size()) {
			context.drawTextWithShadow(EmiPort.literal("›"), nextPage.x() + 4, nextPage.y() + 3,
				nextPage.contains(mouseX, mouseY) ? 0xFF8099FF : 0xFFFFFFFF);
		}
		renderRootGridScrollbar(context, mouseX, mouseY);
	}

	private int rootGridPageCount() {
		return Math.max(1, (ForestManager.size() + rootsPerPage() - 1) / rootsPerPage());
	}

	private void renderRootGridScrollbar(EmiDrawContext context, int mouseX, int mouseY) {
		if (rootGridPageCount() <= 1) {
			return;
		}
		Bounds track = rootGridScrollbarTrack();
		Bounds thumb = rootGridScrollbarThumb();
		context.fill(track.x(), track.y(), track.width(), track.height(), 0xFF202020);
		context.fill(thumb.x(), thumb.y(), thumb.width(), thumb.height(),
			thumb.contains(mouseX, mouseY) ? 0xFF8099FF : 0xFF777777);
	}

	private Bounds rootGridScrollbarTrack() {
		return new Bounds(rootPanelLeft() + rootPanelWidth() - 7, rootListTop(), ROOT_LIST_SCROLLBAR_WIDTH,
			Math.max(0, rootListBottom() - rootListTop()));
	}

	private Bounds rootGridScrollbarThumb() {
		Bounds track = rootGridScrollbarTrack();
		int pageCount = rootGridPageCount();
		int thumbHeight = Math.max(12, track.height() / pageCount);
		int travel = Math.max(0, track.height() - thumbHeight);
		int y = track.y() + (pageCount <= 1 ? 0 : travel * page / (pageCount - 1));
		return new Bounds(track.x(), y, track.width(), thumbHeight);
	}

	private void setRootGridPageFromMouse(double mouseY) {
		Bounds track = rootGridScrollbarTrack();
		Bounds thumb = rootGridScrollbarThumb();
		int travel = Math.max(1, track.height() - thumb.height());
		double position = Mth.clamp(mouseY - track.y() - thumb.height() / 2.0, 0, travel);
		page = Mth.clamp((int) Math.round(position * (rootGridPageCount() - 1) / travel),
			0, rootGridPageCount() - 1);
	}

	private void renderGridBatchAmount(EmiDrawContext context, int x, int y, long amount) {
		Component text = EmiPort.literal(Long.toString(amount));
		int textWidth = Math.max(1, font.width(text));
		float textScale = Math.min(1, 14f / textWidth);
		context.push();
		context.matrices().translate(x + 17, y + 17, 200);
		context.matrices().scale(textScale, textScale, 1);
		context.drawTextWithShadow(text, -textWidth, -9, 0xFFFFFFFF);
		context.pop();
	}

	private void renderRootList(EmiDrawContext context, int mouseX, int mouseY, float delta) {
		clampRootListScroll();
		int left = rootPanelLeft() + 4;
		int right = rootPanelLeft() + rootPanelWidth() - 4 - ROOT_LIST_SCROLLBAR_WIDTH - 2;
		int top = rootListTop();
		int bottom = rootListBottom();
		context.raw().enableScissor(left, top, right, bottom);
		for (int index = 0; index < ForestManager.size(); index++) {
			int y = top + index * ROOT_LIST_ROW_HEIGHT - rootListScroll;
			if (y + ROOT_LIST_ROW_HEIGHT <= top || y >= bottom) {
				continue;
			}
			boolean selected = index == ForestManager.getSelectedIndex();
			boolean hovered = mouseX >= left && mouseX < right && mouseY >= y
				&& mouseY < y + ROOT_LIST_ROW_HEIGHT;
			int border = selected ? 0xFF8099FF : hovered ? 0xFFFFFFFF : 0xFF555555;
			context.fill(left, y + 1, right - left, ROOT_LIST_ROW_HEIGHT - 2, border);
			context.fill(left + 1, y + 2, right - left - 2, ROOT_LIST_ROW_HEIGHT - 4, 0xFF202020);

			MaterialTree tree = ForestManager.getTrees().get(index);
			if (tree != null && tree.goal != null) {
				int itemLeft = rootListItemLeft();
				tree.goal.ingredient.render(context.raw(), itemLeft, y + 4, delta,
					~(EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_REMAINDER));
				int textLeft = itemLeft + 18;
				int textWidth = Math.max(0, rootListControlBounds(y, 2).x() - 2 - textLeft);
				String batchesText = "×" + tree.batches;
				String clipped = font.plainSubstrByWidth(batchesText, textWidth);
				context.drawTextWithShadow(EmiPort.literal(clipped), textLeft, y + 8, 0xFFFFFFFF);
			}

			renderRootListControl(context, rootListControlBounds(y, 0), "−", canDecrementRootListBatch(tree),
				false, mouseX, mouseY);
			renderRootListControl(context, rootListControlBounds(y, 1), "+", true, false, mouseX, mouseY);
			renderRootListControl(context, rootListControlBounds(y, 2), "↑", index > 0, false, mouseX, mouseY);
			renderRootListControl(context, rootListControlBounds(y, 3), "↓", index + 1 < ForestManager.size(),
				false, mouseX, mouseY);
			renderRootListControl(context, rootListControlBounds(y, 4), "×", true, true, mouseX, mouseY);
		}
		context.raw().disableScissor();
		renderRootListScrollbar(context, mouseX, mouseY);
	}

	private void renderRootListScrollbar(EmiDrawContext context, int mouseX, int mouseY) {
		int maxScroll = rootListMaxScroll();
		if (maxScroll <= 0) {
			return;
		}
		Bounds track = rootListScrollbarTrack();
		Bounds thumb = rootListScrollbarThumb();
		context.fill(track.x(), track.y(), track.width(), track.height(), 0xFF202020);
		context.fill(thumb.x(), thumb.y(), thumb.width(), thumb.height(),
			thumb.contains(mouseX, mouseY) ? 0xFF8099FF : 0xFF777777);
	}

	private Bounds rootListScrollbarTrack() {
		return new Bounds(rootPanelLeft() + rootPanelWidth() - 7, rootListTop(), ROOT_LIST_SCROLLBAR_WIDTH,
			Math.max(0, rootListBottom() - rootListTop()));
	}

	private Bounds rootListScrollbarThumb() {
		Bounds track = rootListScrollbarTrack();
		int contentHeight = Math.max(1, ForestManager.size() * ROOT_LIST_ROW_HEIGHT);
		int thumbHeight = Math.max(12, track.height() * track.height() / contentHeight);
		int travel = Math.max(0, track.height() - thumbHeight);
		int y = track.y() + (rootListMaxScroll() == 0 ? 0 : travel * rootListScroll / rootListMaxScroll());
		return new Bounds(track.x(), y, track.width(), thumbHeight);
	}

	private void setRootListScrollFromMouse(double mouseY) {
		Bounds track = rootListScrollbarTrack();
		Bounds thumb = rootListScrollbarThumb();
		int travel = Math.max(1, track.height() - thumb.height());
		double position = Mth.clamp(mouseY - track.y() - thumb.height() / 2.0, 0, travel);
		rootListScroll = (int) Math.round(position * rootListMaxScroll() / travel);
		clampRootListScroll();
	}

	private int rootListMoveDirectionAt(int mouseX, int mouseY) {
		if (!rootPanelContains(mouseX, mouseY) || mouseY < rootListTop() || mouseY >= rootListBottom()) {
			return 0;
		}
		int index = (mouseY - rootListTop() + rootListScroll) / ROOT_LIST_ROW_HEIGHT;
		if (index < 0 || index >= ForestManager.size()) {
			return 0;
		}
		int rowY = rootListTop() + index * ROOT_LIST_ROW_HEIGHT - rootListScroll;
		if (rootListControlBounds(rowY, 2).contains(mouseX, mouseY)) {
			return -1;
		}
		return rootListControlBounds(rowY, 3).contains(mouseX, mouseY) ? 1 : 0;
	}

	private int rootListItemLeft() {
		return rootPanelLeft() + 4 + ROOT_LIST_CONTROL_WIDTH + 2;
	}

	private Bounds rootListControlBounds(int rowY, int slot) {
		int left = rootPanelLeft() + 5;
		int right = rootPanelLeft() + rootPanelWidth() - 4 - ROOT_LIST_SCROLLBAR_WIDTH - 2;
		int deleteLeft = right - ROOT_LIST_RIGHT_MARGIN - ROOT_LIST_CONTROL_WIDTH;
		int arrowLeft = deleteLeft - ROOT_LIST_CONTROL_GAP - ROOT_LIST_CONTROL_WIDTH;
		return switch (slot) {
			case 0 -> new Bounds(left, rowY + 13, ROOT_LIST_CONTROL_WIDTH,
				ROOT_LIST_STACKED_CONTROL_HEIGHT);
			case 1 -> new Bounds(left, rowY + 2, ROOT_LIST_CONTROL_WIDTH,
				ROOT_LIST_STACKED_CONTROL_HEIGHT);
			case 2 -> new Bounds(arrowLeft, rowY + 2, ROOT_LIST_CONTROL_WIDTH,
				ROOT_LIST_STACKED_CONTROL_HEIGHT);
			case 3 -> new Bounds(arrowLeft, rowY + 13, ROOT_LIST_CONTROL_WIDTH,
				ROOT_LIST_STACKED_CONTROL_HEIGHT);
			case 4 -> new Bounds(deleteLeft, rowY + 6, ROOT_LIST_CONTROL_WIDTH, 12);
			default -> Bounds.EMPTY;
		};
	}

	private boolean canDecrementRootListBatch(MaterialTree tree) {
		if (EmiInput.isAltDown()) {
			return ForestManager.getTrees().stream().anyMatch(root -> root.batches > 1);
		}
		return tree != null && tree.batches > 1;
	}

	private void renderRootListControl(EmiDrawContext context, Bounds bounds, String label, boolean enabled,
			boolean danger, int mouseX, int mouseY) {
		boolean hovered = enabled && bounds.contains(mouseX, mouseY);
		int background = danger
			? enabled ? hovered ? 0xFFDD5555 : 0xFF8C2020 : 0xFF4A1818
			: enabled ? hovered ? 0xFF8099FF : 0xFF555555 : 0xFF333333;
		int text = danger ? enabled ? 0xFFFFFFFF : 0xFFAA5555 : enabled ? 0xFFFFFFFF : 0xFF777777;
		context.fill(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background);
		context.drawCenteredTextWithShadow(EmiPort.literal(label), bounds.x() + bounds.width() / 2,
			bounds.y() + Math.max(0, (bounds.height() - 8) / 2), text);
	}

	public int getNodeHeight(MaterialNode node) {
		if (node.recipe != null && node.state == FoldState.EXPANDED) {
			int i = 1;
			for (MaterialNode n : node.children) {
				i = Math.max(i, getNodeHeight(n));
			}
			if (node.recipe instanceof EmiResolutionRecipe) {
				return i;
			}
			return i + 1;
		}
		return 1;
	}

	private TreeVolume forest$addNewNodes(MaterialNode node, long multiplier, long divisor, int depth, ChanceState chance) {
		if (EmiCompatibility.isCatalyst(node)) {
			multiplier = node.amount;
		} else {
			multiplier = node.amount * (int) Math.ceil(multiplier / (float) divisor);
		}
		if (node.recipe != null && node.children.size() > 0 && node.state == FoldState.EXPANDED) {
			ChanceState produced = chance.produce(node.produceChance);
			if (node.recipe instanceof EmiResolutionRecipe) {
				TreeVolume volume = forest$addNewNodes(node.children.get(0), multiplier, node.divisor, depth, produced);
				volume.nodes.get(0).resolution = node;
				return volume;
			}
			TreeVolume left = null;
			for (int i = 0; i < node.children.size(); i++) {
				ChanceState consumed = produced.consume(node.children.get(i).consumeChance);
				TreeVolume volume = forest$addNewNodes(node.children.get(i), multiplier, node.divisor, depth + 1, consumed);
				if (left == null) {
					left = volume;
				} else {
					left.addToRight(volume);
				}
			}
			left.addHead(node, multiplier, depth * NODE_VERTICAL_SPACING, chance);
			return left;
		}
		return new TreeVolume(node, multiplier, depth * NODE_VERTICAL_SPACING, chance);
	}

	private static void drawLine(EmiDrawContext context, int x1, int y1, int x2, int y2) {
		if (x2 < x1) {
			drawLine(context, x2, y1, x1, y2);
			return;
		}
		if (y2 < y1) {
			drawLine(context, x1, y2, x2, y1);
			return;
		}
		context.fill(x1, y1, x2 - x1 + 1, y2 - y1 + 1, 0xFFFFFFFF);
	}

	public float getScale() {
		zoom = Mth.clamp(zoom, -6, 4);
		int scale = (int) this.minecraft.getWindow().getGuiScale();
		int desired = scale + zoom;
		if (desired < 1) {
			zoom -= desired - 1;
			desired = 1;
		}
		return (float) desired / scale;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.onClose();
			return true;
		} else if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
			this.onClose();
			return true;
		}
		Function<EmiBind, Boolean> function = bind -> bind.matchesKey(keyCode, scanCode);
		if (function.apply(EmiConfig.back)) {
			EmiHistory.pop();
			return true;
		}
		Hover hover = forest$getHoveredStack(lastMouseX, lastMouseY);
		if (hover != null && hover.stack != null && !hover.stack.isEmpty()) {
			if (function.apply(EmiConfig.favorite)) {
				EmiFavorites.addFavorite(hover.stack, hover.node == null ? null : hover.node.recipe);
			}
		}
		if (EmiInput.isControlDown()
				&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			BookmarkNameScreen.openForSave(this);
			return true;
		} else if (EmiInput.isControlDown() && keyCode == GLFW.GLFW_KEY_R) {
			List<EmiRecipe> recipes = EmiApi.getRecipeManager().getRecipes();
			if (recipes.size() > 0) {
				for (int i = 0; i < 100_000; i++) {
					EmiRecipe recipe = recipes.get(EmiUtil.RANDOM.nextInt(recipes.size()));
					if (recipe.supportsRecipeTree()) {
						ForestManager.replaceSolo(recipe);
						init();
						return true;
					}
				}
			}
		} else if (EmiInput.isControlDown() && keyCode == GLFW.GLFW_KEY_C) {
			ForestManager.clear();
			init();
		}
		return false;
	}

	private boolean getAutoResolutions(Hover hover, BiConsumer<EmiIngredient, EmiRecipe> consumer) {
		EmiPlayerInventory inv = playerInv;
		if (inv != null) {
			List<EmiStack> stacks = hover.stack.getEmiStacks();
			if (stacks.size() > 1) {
				for (EmiStack stack : stacks) {
					if (inv.inventory.containsKey(stack)) {
						consumer.accept(hover.stack, new EmiResolutionRecipe(hover.stack, stack));
						return true;
					}
				}
				for (EmiStack stack : stacks) {
					for (Cost cost : costs) {
						if (cost.cost.ingredient.equals(stack)) {
							consumer.accept(hover.stack, new EmiResolutionRecipe(hover.stack, stack));
							return true;
						}
					}
				}
				consumer.accept(hover.stack, new EmiResolutionRecipe(hover.stack, stacks.get(0)));
				return true;
			} else if (EmiApi.getHandledScreen() != null) {
				EmiRecipe recipe = EmiUtil.getRecipeResolution(hover.stack, inv);
				if (recipe != null) {
					consumer.accept(hover.stack, recipe);
					return true;
				}
			}
		}
		return false;
	}

	private boolean applyPreferredResolution(Hover hover, ResolutionScope forestScope) {
		EmiRecipe recipe = hover.node == null ? null : hover.node.recipe;
		if (recipe != null && recipe.supportsRecipeTree()) {
			applyResolution(hover, hover.stack, recipe, forestScope);
			return true;
		}
		return getAutoResolutions(hover,
			(ingredient, preferred) -> applyResolution(hover, ingredient, preferred, forestScope));
	}

	private void applyResolution(Hover hover, EmiIngredient ingredient, EmiRecipe recipe, ResolutionScope forestScope) {
		if (forestScope == null && hover.node != null && BoM.tree != null) {
			BoM.tree.addResolution(ingredient, recipe);
		} else if (forestScope != null) {
			ForestManager.addResolution(ingredient, recipe, forestScope);
		}
	}

	private void openResolutionPicker(Hover hover, ResolutionScope pendingScope) {
		ForestManager.cancelPendingResolution();
		EmiApi.displayRecipes(hover.stack);
		Minecraft client = Minecraft.getInstance();
		if (!(client.screen instanceof RecipeScreen recipeScreen)) {
			ForestManager.cancelPendingResolution();
			return;
		}
		if (pendingScope != null) {
			ForestManager.beginPendingResolution(hover.stack, pendingScope);
		}
		RecipeScreen.resolve = hover.stack;
		// The first init doesn't realize a resolution exists so we do it again. What
		// could go wrong.
		recipeScreen.init(client, recipeScreen.width, recipeScreen.height);
		if (hover.node != null) {
			if (hover.node.recipe != null) {
				EmiApi.focusRecipe(hover.node.recipe);
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 || button == 2) {
			rootPanelDrag = rootPanelContains(mouseX, mouseY);
		}
		if (handleRootPanelClick(mouseX, mouseY, button)) {
			return true;
		}
		if (rootPanelContains(mouseX, mouseY)) {
			return true;
		}
		Hover hover = forest$getHoveredStack((int) mouseX, (int) mouseY);
		float scale = getScale();
		int mx = (int) ((mouseX - contentCenterX()) / scale - offX);
		int my = (int) ((mouseY - contentCenterY()) / scale - offY);
		if (hover != null) {
			if (button == 1 && hover.node != null && hover.node.recipe != null) {
				if (EmiInput.isShiftDown()) {
					ForestManager.cancelPendingResolution();
					applyResolution(hover, hover.node.ingredient, null, null);
				} else if (!(hover.node.recipe instanceof EmiResolutionRecipe)) {
					if (hover.node.state == FoldState.EXPANDED) {
						hover.node.state = FoldState.COLLAPSED;
					} else {
						hover.node.state = FoldState.EXPANDED;
					}
				}
				recalculateTree();
				return true;
			}
			if (hover.stack != null) {
				if (EmiInput.isShiftDown() && button == 0) {
					ForestManager.cancelPendingResolution();
					ResolutionScope forestScope = hover.node == null ? ForestBookmarks.getResolutionScope() : null;
					if (applyPreferredResolution(hover, forestScope)) {
						recalculateTree();
					} else {
						openResolutionPicker(hover, forestScope);
					}
					return true;
				} else {
					if (button == 0) {
						openResolutionPicker(hover,
							hover.node == null ? ForestBookmarks.getResolutionScope() : null);
						return true;
					}
				}
			}
		} else if (mode.contains(mx, my)) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
			ForestManager.toggleCraftingMode();
			recalculateTree();
		} else if (batches.contains(mx, my) && BoM.tree != null) {
			boolean changed = false;
			for (MaterialTree tree : batchTargets()) {
				long ideal = tree.cost.getIdealBatch(tree.goal, 1, 1);
				if (ideal != tree.batches) {
					tree.batches = ideal;
					changed = true;
				}
			}
			if (changed) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				recalculateTree();
			}
		}
		Function<EmiBind, Boolean> function = bind -> bind.matchesMouse(button);
		if (function.apply(EmiConfig.back)) {
			EmiHistory.pop();
			return true;
		}
		return false;
	}

	private boolean handleRootPanelClick(double mouseX, double mouseY, int button) {
		if (!rootPanelContains(mouseX, mouseY)) {
			return false;
		}
		if (!rootPanelHasContent()) {
			return true;
		}
		if (button == 0 && rootLayoutToggle.contains((int) mouseX, (int) mouseY)) {
			ForestBookmarks.setRootLayout(ForestBookmarks.getRootLayout() == ForestBookmarks.RootLayout.LIST
				? ForestBookmarks.RootLayout.GRID : ForestBookmarks.RootLayout.LIST);
			page = 0;
			recalculateTree();
			return true;
		}
		if (ForestBookmarks.getRootLayout() != ForestBookmarks.RootLayout.GRID) {
			if (button == 0 && rootListMaxScroll() > 0
					&& rootListScrollbarTrack().contains((int) mouseX, (int) mouseY)) {
				rootListScrollbarDrag = true;
				setRootListScrollFromMouse(mouseY);
				return true;
			}
			if ((button == 0 || button == 1) && mouseY >= rootListTop() && mouseY < rootListBottom()) {
				int index = ((int) mouseY - rootListTop() + rootListScroll) / ROOT_LIST_ROW_HEIGHT;
				if (index >= 0 && index < ForestManager.size()) {
					int rowY = rootListTop() + index * ROOT_LIST_ROW_HEIGHT - rootListScroll;
					if (button == 1) {
						MaterialTree tree = ForestManager.getTrees().get(index);
						tree.batches = Math.max(1, tree.cost.getIdealBatch(tree.goal, 1, 1));
						ForestManager.select(index);
					} else if (rootListControlBounds(rowY, 0).contains((int) mouseX, (int) mouseY)) {
						adjustRootListBatch(index, -1);
					} else if (rootListControlBounds(rowY, 1).contains((int) mouseX, (int) mouseY)) {
						adjustRootListBatch(index, 1);
					} else if (rootListControlBounds(rowY, 2).contains((int) mouseX, (int) mouseY)) {
						moveRootListEntry(index, -1);
					} else if (rootListControlBounds(rowY, 3).contains((int) mouseX, (int) mouseY)) {
						moveRootListEntry(index, 1);
					} else if (rootListControlBounds(rowY, 4).contains((int) mouseX, (int) mouseY)) {
						ForestManager.remove(index);
					} else {
						ForestManager.select(index);
					}
					clampRootListScroll();
					recalculateTree();
				}
			}
			return rootPanelContains(mouseX, mouseY);
		}
		if (button == 0 && page > 0 && previousPage.contains((int) mouseX, (int) mouseY)) {
			page--;
			return true;
		}
		if (button == 0 && (page + 1) * rootsPerPage() < ForestManager.size()
				&& nextPage.contains((int) mouseX, (int) mouseY)) {
			page++;
			return true;
		}
		if (button == 0 && rootGridPageCount() > 1
				&& rootGridScrollbarTrack().contains((int) mouseX, (int) mouseY)) {
			rootGridScrollbarDrag = true;
			setRootGridPageFromMouse(mouseY);
			return true;
		}
		int columns = rootColumns();
		int rows = rootRows();
		int left = rootPanelLeft() + (rootPanelWidth() - columns * ROOT_CELL_SIZE) / 2;
		int localX = (int) mouseX - left;
		int localY = (int) mouseY - rootPanelTop() - ROOT_PANEL_HEADER_HEIGHT;
		if (localX < 0 || localY < 0 || localX >= columns * ROOT_CELL_SIZE
				|| localY >= rows * ROOT_CELL_SIZE) {
			return rootPanelContains(mouseX, mouseY);
		}
		int column = localX / ROOT_CELL_SIZE;
		int row = localY / ROOT_CELL_SIZE;
		int index = page * rootsPerPage() + row * columns + column;
		if (index >= ForestManager.size()) {
			return true;
		}
		int withinX = localX % ROOT_CELL_SIZE;
		int withinY = localY % ROOT_CELL_SIZE;
		if (button == 0 && withinX >= 12 && withinY < 6) {
			ForestManager.remove(index);
		} else if (button == 1) {
			MaterialTree tree = ForestManager.getTrees().get(index);
			tree.batches = Math.max(1, tree.cost.getIdealBatch(tree.goal, 1, 1));
			ForestManager.select(index);
		} else if (button == 0) {
			ForestManager.select(index);
		} else {
			return false;
		}
		recalculateTree();
		return true;
	}

	private void moveRootListEntry(int index, int direction) {
		if (index < 0 || index >= ForestManager.size() || direction == 0) {
			return;
		}
		int target;
		if (EmiInput.isShiftDown()) {
			target = direction < 0 ? 0 : ForestManager.size() - 1;
		} else {
			int distance = EmiInput.isAltDown() ? 10 : EmiInput.isControlDown() ? 5 : 1;
			target = Mth.clamp(index + direction * distance, 0, ForestManager.size() - 1);
		}
		if (target != index) {
			ForestManager.move(index, target);
		}
	}

	private void adjustRootListBatch(int index, long amount) {
		if (index < 0 || index >= ForestManager.size()) {
			return;
		}
		List<MaterialTree> targets = EmiInput.isAltDown()
			? ForestManager.getTrees() : List.of(ForestManager.getTrees().get(index));
		for (MaterialTree tree : targets) {
			adjustBatch(tree, amount);
		}
	}

	private List<MaterialTree> batchTargets() {
		return EmiInput.isAltDown() ? ForestManager.getTrees()
			: BoM.tree == null ? List.of() : List.of(BoM.tree);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
		if (rootPanelContains(mouseX, mouseY)) {
			int index = ForestBookmarks.getRootLayout() == ForestBookmarks.RootLayout.GRID
				? rootGridIndexAt(mouseX, mouseY) : rootListBatchIndexAt(mouseX, mouseY);
			if (index >= 0) {
				long adjustment = consumeRootBatchScroll(index, amount);
				if (adjustment != 0) {
					adjustRootListBatch(index, adjustment);
					recalculateTree();
				}
			} else {
				resetRootBatchScroll();
				if (ForestBookmarks.getRootLayout() == ForestBookmarks.RootLayout.LIST
						&& mouseY >= rootListTop() && mouseY < rootListBottom()) {
					rootListScroll -= (int) Math.round(amount * ROOT_LIST_ROW_HEIGHT);
					clampRootListScroll();
				}
			}
			return true;
		}
		resetRootBatchScroll();
		scrollAcc += amount;
		amount = (int) scrollAcc;
		scrollAcc %= 1;
		float scale = getScale();
		int mx = (int) ((mouseX - contentCenterX()) / scale - offX);
		int my = (int) ((mouseY - contentCenterY()) / scale - offY);
		if (BoM.tree != null && batches.contains(mx, my)) {
			for (MaterialTree tree : batchTargets()) {
				adjustBatch(tree, (long) amount);
			}
			recalculateTree();
			return true;
		}
		zoom += (int) amount;
		return true;
	}

	private int rootGridIndexAt(double mouseX, double mouseY) {
		int columns = rootColumns();
		int rows = rootRows();
		int left = rootPanelLeft() + (rootPanelWidth() - columns * ROOT_CELL_SIZE) / 2;
		int localX = (int) mouseX - left;
		int localY = (int) mouseY - rootPanelTop() - ROOT_PANEL_HEADER_HEIGHT;
		if (localX < 0 || localY < 0 || localX >= columns * ROOT_CELL_SIZE
				|| localY >= rows * ROOT_CELL_SIZE
				|| localX % ROOT_CELL_SIZE >= 18 || localY % ROOT_CELL_SIZE >= 18) {
			return -1;
		}
		int index = page * rootsPerPage()
			+ localY / ROOT_CELL_SIZE * columns + localX / ROOT_CELL_SIZE;
		return index < ForestManager.size() ? index : -1;
	}

	private int rootListBatchIndexAt(double mouseX, double mouseY) {
		if (mouseY < rootListTop() || mouseY >= rootListBottom()) {
			return -1;
		}
		int index = ((int) mouseY - rootListTop() + rootListScroll) / ROOT_LIST_ROW_HEIGHT;
		if (index < 0 || index >= ForestManager.size()) {
			return -1;
		}
		int rowY = rootListTop() + index * ROOT_LIST_ROW_HEIGHT - rootListScroll;
		Bounds minus = rootListControlBounds(rowY, 0);
		int right = rootListControlBounds(rowY, 2).x() - ROOT_LIST_CONTROL_GAP;
		Bounds batchRegion = new Bounds(minus.x(), rowY + 1, Math.max(0, right - minus.x()),
			ROOT_LIST_ROW_HEIGHT - 2);
		return batchRegion.contains((int) mouseX, (int) mouseY) ? index : -1;
	}

	private long consumeRootBatchScroll(int index, double amount) {
		if (rootBatchScrollIndex != index) {
			rootBatchScrollAcc = 0;
			rootBatchScrollIndex = index;
		}
		rootBatchScrollAcc += amount;
		long adjustment = (long) rootBatchScrollAcc;
		rootBatchScrollAcc %= 1;
		return adjustment;
	}

	private void resetRootBatchScroll() {
		rootBatchScrollAcc = 0;
		rootBatchScrollIndex = -1;
	}

	private void adjustBatch(MaterialTree tree, long amount) {
		long adjustment = amount;
		if (EmiInput.isShiftDown()) {
			adjustment *= 16;
		} else if (EmiInput.isControlDown()) {
			adjustment = amount > 0 ? tree.batches : -tree.batches / 2;
		}
		if (tree.batches == 1 && adjustment > 1) {
			tree.batches = adjustment;
		} else {
			tree.batches += adjustment;
		}
		tree.batches = Math.max(1, tree.batches);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (rootListScrollbarDrag && button == 0) {
			setRootListScrollFromMouse(mouseY);
			return true;
		}
		if (rootGridScrollbarDrag && button == 0) {
			setRootGridPageFromMouse(mouseY);
			return true;
		}
		if (rootPanelDrag && (button == 0 || button == 2)) {
			return true;
		}
		if (button == 0 || button == 2) {
			float scale = getScale();
			offX += deltaX / scale;
			offY += deltaY / scale;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean consumed = rootPanelDrag && (button == 0 || button == 2)
			|| (rootListScrollbarDrag || rootGridScrollbarDrag) && button == 0;
		if (button == 0 || button == 2) {
			rootPanelDrag = false;
			rootListScrollbarDrag = false;
			rootGridScrollbarDrag = false;
		}
		return consumed || super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ForestManager.cancelPendingResolution();
		Minecraft.getInstance().setScreen(old);
	}

	private class Cost {
		public FlatMaterialCost cost;
		public int x, y;
		public long alreadyDone = 0;
		public boolean remainder;

		public Cost(FlatMaterialCost cost, int x, int y, boolean remainder) {
			this.cost = cost;
			this.x = x;
			this.y = y;
			this.remainder = remainder;
		}

		public void renderIcon(EmiDrawContext context) {
			batcher.render(cost.ingredient, context.raw(), x, y, 0, ~(EmiIngredient.RENDER_AMOUNT | EmiIngredient.RENDER_REMAINDER));
		}

		public void renderAmount(EmiDrawContext context) {
			if (EmiInput.isAltDown() && ForestBookmarks.getQuantityMode() == ForestBookmarks.QuantityMode.ICON
					&& renderCostQuantityIcons(context, this)) {
				return;
			}
			EmiRenderHelper.renderAmount(context, x, y, getAmountText());
		}

		public Component getAmountText() {
			return getAmountText(EmiInput.isAltDown());
		}

		public int getReservedAmountOverflow() {
			if (!altLayout) {
				return EmiRenderHelper.getAmountOverflow(getAmountText(false));
			}
			return ForestBookmarks.getQuantityMode() == ForestBookmarks.QuantityMode.ICON
				? getCostQuantityIconWidth(this) : EmiRenderHelper.getAmountOverflow(getAmountText(true));
		}

		private Component getAmountText(boolean decomposed) {
			long adjusted = cost.getEffectiveAmount();
			Component totalText;
			if (cost instanceof ChanceMaterialCost cmc) {
				totalText = EmiPort.append(EmiPort.literal("≈"), amountText(cost.ingredient, adjusted, decomposed))
					.withStyle(ChatFormatting.GOLD);
			} else {
				totalText = amountText(cost.ingredient, adjusted, decomposed);
			}
			if (!remainder && BoM.craftingMode) {
				long amount = alreadyDone;
				if (amount < adjusted) {
					Component doneText = amount == 0 ? EmiPort.literal("0")
						: amountText(cost.ingredient, amount, decomposed);
					MutableComponent text = EmiPort.append(EmiPort.literal("", ChatFormatting.RED), doneText);
					text = EmiPort.append(text, EmiPort.literal("/"));
					text = EmiPort.append(text, totalText);
					return text;
				}
			}
			return totalText;
		}
	}

	private Component amountText(EmiIngredient ingredient, long amount, boolean decomposed) {
		if (decomposed && !ingredient.getEmiStacks().isEmpty()) {
			var itemStack = ingredient.getEmiStacks().get(0).getItemStack();
			if (!itemStack.isEmpty() && itemStack.getMaxStackSize() > 1) {
				DisplayMode mode = ForestBookmarks.getQuantityMode() == ForestBookmarks.QuantityMode.ICON
					? DisplayMode.ICON : DisplayMode.TEXT;
				QuantityDisplay display = QuantityDisplay.decompose(Math.max(0, amount),
					itemStack.getMaxStackSize(), ForestBookmarks.isBoxEnabled(),
					ForestBookmarks.getStacksPerBox(), mode);
				List<Component> units = Lists.newArrayList();
				if (display.hasBoxes()) {
					units.add(Component.translatable("screen.emi_recipeforest.quantity.text.box",
						display.boxes(), ForestBookmarks.getStacksPerBox(), display.stackUnitCapacity()));
				}
				if (display.hasFullStacks()) {
					units.add(Component.translatable("screen.emi_recipeforest.quantity.text.stack",
						display.fullStacks(), display.stackUnitCapacity()));
				}
				if (display.hasItems()) {
					units.add(Component.translatable("screen.emi_recipeforest.quantity.text.item", display.items()));
				}
				if (units.isEmpty()) {
					return Component.translatable("screen.emi_recipeforest.quantity.text.zero");
				}
				MutableComponent result = EmiPort.append(EmiPort.literal(""), units.get(0));
				for (int i = 1; i < units.size(); i++) {
					result = EmiPort.append(result, EmiPort.literal(" + "));
					result = EmiPort.append(result, units.get(i));
				}
				return result;
			}
		}
		return EmiRenderHelper.getAmountText(ingredient, amount);
	}

	private QuantityDisplay quantityDisplay(EmiIngredient ingredient, long amount) {
		if (ingredient.getEmiStacks().isEmpty()) {
			return null;
		}
		var itemStack = ingredient.getEmiStacks().get(0).getItemStack();
		if (itemStack.isEmpty() || itemStack.getMaxStackSize() <= 1) {
			return null;
		}
		return QuantityDisplay.decompose(Math.max(0, amount), itemStack.getMaxStackSize(),
			ForestBookmarks.isBoxEnabled(), ForestBookmarks.getStacksPerBox(), DisplayMode.ICON);
	}

	private int quantityIconWidth(QuantityDisplay display) {
		if (display == null) {
			return 0;
		}
		int width = 0;
		if (display.hasBoxes()) {
			width += quantityUnitWidth(display.boxes(), 0);
		}
		if (display.hasFullStacks()) {
			width += quantityUnitWidth(display.fullStacks(), display.stackUnitCapacity());
		}
		if (display.hasItems()) {
			width += quantityUnitWidth(display.items(), 0);
		}
		return Math.max(0, width - 2);
	}

	private int quantityUnitWidth(long count, long capacity) {
		String badge = capacity != 0 && capacity != 16 && capacity != 64 ? "/" + capacity : "";
		return 18 + font.width(Long.toString(count)) + font.width(badge);
	}

	private int renderQuantityIcons(EmiDrawContext context, int x, int y, QuantityDisplay display) {
		int cursor = x;
		if (display.hasBoxes()) {
			cursor = renderQuantityUnit(context, cursor, y, 0, display.boxes(), 0);
		}
		if (display.hasFullStacks()) {
			int u = display.stackUnitCapacity() == 16 ? 16 : 32;
			cursor = renderQuantityUnit(context, cursor, y, u, display.fullStacks(),
				display.stackUnitCapacity());
		}
		if (display.hasItems()) {
			cursor = renderQuantityUnit(context, cursor, y, 48, display.items(), 0);
		}
		return cursor;
	}

	private int renderQuantityUnit(EmiDrawContext context, int x, int y, int u, long count, long capacity) {
		context.drawTexture(RECIPE_FOREST_WIDGETS, x, y, 0, u, 0, 16, 16, 64, 32);
		String countText = Long.toString(count);
		context.drawTextWithShadow(EmiPort.literal(countText), x + 16, y + 7, 0xFFFFFFFF);
		int cursor = x + 16 + font.width(countText);
		if (capacity != 0 && capacity != 16 && capacity != 64) {
			String badge = "/" + capacity;
			context.drawTextWithShadow(EmiPort.literal(badge), cursor, y + 7, 0xFFFFAA00);
			cursor += font.width(badge);
		}
		return cursor + 2;
	}

	private int getCostQuantityIconWidth(Cost cost) {
		long adjusted = cost.cost.getEffectiveAmount();
		int total = quantityIconWidth(quantityDisplay(cost.cost.ingredient, adjusted));
		if (total == 0) {
			return EmiRenderHelper.getAmountOverflow(cost.getAmountText(true));
		}
		if (cost.cost instanceof ChanceMaterialCost) {
			total += font.width("≈");
		}
		if (!cost.remainder && BoM.craftingMode && cost.alreadyDone < adjusted) {
			int done = cost.alreadyDone == 0 ? font.width("0")
				: quantityIconWidth(quantityDisplay(cost.cost.ingredient, cost.alreadyDone));
			total += done + font.width("/");
		}
		return total;
	}

	private boolean renderCostQuantityIcons(EmiDrawContext context, Cost cost) {
		long adjusted = cost.cost.getEffectiveAmount();
		QuantityDisplay total = quantityDisplay(cost.cost.ingredient, adjusted);
		if (total == null) {
			return false;
		}
		int cursor = cost.x + 16;
		if (!cost.remainder && BoM.craftingMode && cost.alreadyDone < adjusted) {
			QuantityDisplay done = quantityDisplay(cost.cost.ingredient, cost.alreadyDone);
			if (done == null || quantityIconWidth(done) == 0) {
				context.drawTextWithShadow(EmiPort.literal("0"), cursor, cost.y + 7, 0xFFFF5555);
				cursor += font.width("0");
			} else {
				cursor = renderQuantityIcons(context, cursor, cost.y, done);
			}
			context.drawTextWithShadow(EmiPort.literal("/"), cursor, cost.y + 7, 0xFFFFFFFF);
			cursor += font.width("/");
		}
		if (cost.cost instanceof ChanceMaterialCost) {
			context.drawTextWithShadow(EmiPort.literal("≈"), cursor, cost.y + 7, 0xFFFFAA00);
			cursor += font.width("≈");
		}
		renderQuantityIcons(context, cursor, cost.y, total);
		return true;
	}

	private class Hover {
		public EmiIngredient stack;
		public MaterialNode node, resolve;
		public EmiRecipeCategory category;

		public Hover(EmiIngredient stack) {
			this.stack = stack;
		}

		public Hover(EmiIngredient stack, MaterialNode node, MaterialNode resolve) {
			this.stack = stack;
			this.node = node;
			this.resolve = resolve;
		}

		public Hover(EmiRecipeCategory category, MaterialNode node) {
			this.category = category;
			this.node = node;
		}

		public Hover(MaterialNode node) {
			this.node = node;
		}

		public boolean drawTooltip(Screen screen, EmiDrawContext context, int mouseX, int mouseY) {
			if (stack != null) {
				List<ClientTooltipComponent> list = Lists.newArrayList();
				list.addAll(stack.getTooltip());
				if (EmiInput.isShiftDown()) {
					getAutoResolutions(this, (stack, recipe) -> {
						if (node == null || recipe != node.recipe) {
							list.add(new RecipeTooltipComponent(recipe, 0x4488FFAA));
						} else {
							list.add(new RecipeTooltipComponent(recipe));
						}
					});
				} else if (node != null && node.recipe != null) {
					list.add(new RecipeTooltipComponent(node.recipe));
				}
				if (node != null) {
					if (node.consumeChance != 1) {
						list.add(EmiTooltip.chance("consume", node.consumeChance));
					} else if (resolve != null && resolve.consumeChance != 1) {
						list.add(EmiTooltip.chance("consume", resolve.consumeChance));
					}
					if (node.produceChance != 1) {
						list.add(EmiTooltip.chance("produce", node.produceChance));
					}
				}
				EmiRenderHelper.drawTooltip(screen, context, list, mouseX, mouseY);
				return true;
			} else if (category != null) {
				EmiRenderHelper.drawTooltip(screen, context, category.getTooltip(), mouseX, mouseY);
				return true;
			}
			return false;
		}
	}

	private class Node {
		public Node parent = null;
		public MaterialNode resolution = null;
		public MaterialNode node;
		public int width, x, y, midOffset;
		public long amount;
		public ChanceState chance;

		public Node(MaterialNode node, long amount, int x, int y, ChanceState chance) {
			this.node = node;
			if (node.recipe != null) {
				width = 42;
			} else {
				width = 16;
			}
			this.amount = amount;
			this.x = x;
			this.y = y;
			this.chance = chance;
			int tw;
			if (altLayout) {
				tw = ForestBookmarks.getQuantityMode() == ForestBookmarks.QuantityMode.ICON
					? getQuantityIconWidth() : EmiRenderHelper.getAmountOverflow(getAmountText(true));
			} else {
				tw = EmiRenderHelper.getAmountOverflow(getAmountText(false));
			}
			width += tw;
			midOffset = tw / -2;
		}

		public void render(EmiDrawContext context, int mouseX, int mouseY, float delta) {
			if (parent != null) {
				context.push();

				setColor(context, parent.node, node.consumeChance != 1 || (resolution != null && resolution.consumeChance != 1), false);

				int nx = x;
				int ny = y;
				int px = parent.x;
				int py = parent.y;
				int off = NODE_VERTICAL_SPACING - 1;
				if (resolution != null) {
					context.drawTexture(EmiRenderHelper.WIDGETS, x - 3, y - 19, 9, 192, 7, 7);
					drawLine(context, nx, y - 12, nx, ny - 11);
					drawLine(context, nx, py + off, nx, y - 19);
				} else {
					drawLine(context, nx, ny - 11, nx, py + off);
				}
				setColor(context, parent.node, false, false);
				drawLine(context, px, py + off, nx, py + off);
				context.pop();
			}
			int xo = 0;
			if (node.recipe != null) {
				int lx = x - width / 2;
				int ly = y - 11;
				int hx = x + width / 2;
				int hy = y + 10;
				context.push();

				setColor(context, node, node.produceChance != 1, false);

				if (node.state != FoldState.EXPANDED) {
					drawLine(context, x, hy + 1, x, hy + 3);
				} else {
					drawLine(context, x, hy + 1, x, hy + 8);
				}

				boolean hovered = mouseX >= lx && mouseY >= ly && mouseX <= hx && mouseY <= hy;
				setColor(context, node, node.produceChance != 1, hovered);
				drawLine(context, lx, ly, lx, hy);
				drawLine(context, hx, ly, hx, hy);
				drawLine(context, lx, ly, hx, ly);
				drawLine(context, lx, hy, hx, hy);
				EmiRecipeCategory cat = node.recipe.getCategory();
				if (StackBatcher.isEnabled() && EmiRecipeCategoryProperties.getSimplifiedIcon(cat) instanceof Batchable b) {
					batcher.render(b, context.raw(), x - 18 + midOffset, y - 8, delta);
				} else {
					cat.renderSimplified(context.raw(), x - 18 + midOffset, y - 8, delta);
				}
				xo = 11;
				context.pop();
			}
			context.setColor(1f, 1f, 1f, 1f);
			batcher.render(node.ingredient, context.raw(), x + xo - 8 + midOffset, y - 8, 0);
		}

		public void renderAmount(EmiDrawContext context) {
			int xo = node.recipe == null ? 0 : 11;
			if (EmiInput.isAltDown() && ForestBookmarks.getQuantityMode() == ForestBookmarks.QuantityMode.ICON) {
				long displayAmount = getDisplayAmount();
				QuantityDisplay display = quantityDisplay(node.ingredient, displayAmount);
				if (display != null) {
					int cursor = x + xo + 8 + midOffset;
					if (chance.chanced()) {
						context.drawTextWithShadow(EmiPort.literal("≈"), cursor, y - 1, 0xFFFFAA00);
						cursor += font.width("≈");
					}
					renderQuantityIcons(context, cursor, y - 8, display);
					return;
				}
			}
			EmiRenderHelper.renderAmount(context, x + xo - 8 + midOffset, y - 8, getAmountText());
		}

		private long getDisplayAmount() {
			if (chance.chanced()) {
				return Math.max(Math.round(amount * chance.chance()), node.amount);
			}
			return amount;
		}

		private int getQuantityIconWidth() {
			QuantityDisplay display = quantityDisplay(node.ingredient, getDisplayAmount());
			if (display == null) {
				return EmiRenderHelper.getAmountOverflow(getAmountText(true));
			}
			return quantityIconWidth(display) + (chance.chanced() ? font.width("≈") : 0);
		}

		public void setColor(EmiDrawContext context, MaterialNode node, boolean chanced, boolean hovered) {
			context.setColor(1f, 1f, 1f, 1f);
			if (chanced) {
				context.setColor(0.8f, 0.6f, 0.1f, 1f);
			}
			if (BoM.craftingMode) {
				if (node.progress == ProgressState.COMPLETED) {
					context.setColor(0.1f, 0.8f, 0.5f, 1f);
				} else if (node.progress == ProgressState.PARTIAL) {
					context.setColor(0.8f, 0.2f, 0.9f, 1f);
				}
			}
			if (hovered) {
				context.setColor(0.5f, 0.6f, 1f, 1f);
			}
		}

		public Component getAmountText() {
			return getAmountText(EmiInput.isAltDown());
		}

		private Component getAmountText(boolean decomposed) {
			if (chance.chanced()) {
				long a = Math.round(amount * chance.chance());
				a = Math.max(a, node.amount);
				return EmiPort.append(EmiPort.literal("≈"),
						amountText(node.ingredient, a, decomposed))
					.withStyle(ChatFormatting.GOLD);
			} else {
				return amountText(node.ingredient, amount, decomposed);
			}
		}

		public Hover getHover(int mouseX, int mouseY) {
			if (resolution != null) {
				if (mouseX >= x - 4 && mouseX < x + 4 && mouseY >= y - 19 && mouseY < y - 11) {
					return new Hover(resolution.ingredient, resolution, null);
				}
			}
			int imx = mouseX;
			if (node.recipe != null) {
				if (mouseX >= x - 18 + midOffset && mouseX < x - 2 + midOffset && mouseY >= y - 8 && mouseY < y + 8) {
					return new Hover(node.recipe.getCategory(), node);
				}
				imx -= 11;
			}
			if (imx >= x - 8 + midOffset && imx < x + 8 + midOffset && mouseY >= y - 8 && mouseY < y + 8) {
				return new Hover(node.ingredient, node, resolution);
			}
			int lx = x - width / 2;
			int ly = y - 11;
			int hx = x + width / 2;
			int hy = y + 10;
			if (mouseX >= lx && mouseY >= ly && mouseX <= hx && mouseY <= hy) {
				return new Hover(node);
			}
			return null;
		}
	}

	private class TreeVolume {
		public List<Width> widths = Lists.newArrayList();
		public List<Node> nodes = Lists.newArrayList();

		public TreeVolume(MaterialNode node, long amount, int y, ChanceState chance) {
			Node head = new Node(node, amount, 0, y, chance);
			int l = head.width / 2;
			widths.add(new Width(-l, head.width - l));
			nodes.add(head);
		}

		public void addHead(MaterialNode node, long amount, int y, ChanceState chance) {
			int x = (getLeft(0) + getRight(0)) / 2;
			Node newNode = new Node(node, amount, x, y, chance);
			for (Node n : nodes) {
				if (n.parent == null) {
					n.parent = newNode;
				}
				n.y += NODE_VERTICAL_SPACING;
			}
			int l = newNode.width / 2;
			widths.add(0, new Width(x - l, x + newNode.width - l));
			nodes.add(0, newNode);
		}

		public int getDepth() {
			return widths.size();
		}

		public int getMinLeft() {
			int m = getLeft(0);
			for (int i = 1; i < getDepth(); i++) {
				m = Math.min(m, getLeft(i));
			}
			return m;
		}

		public int getMaxRight() {
			int m = getRight(0);
			for (int i = 1; i < getDepth(); i++) {
				m = Math.max(m, getRight(i));
			}
			return m;
		}

		public int getLeft(int depth) {
			return widths.get(depth).left;
		}

		public int getRight(int depth) {
			return widths.get(depth).right;
		}

		public void addToRight(TreeVolume other) {
			int rOff = getRight(0) - other.getLeft(0) + NODE_HORIZONTAL_SPACING;
			for (int i = 1; i < getDepth() && i < other.getDepth(); i++) {
				rOff = Math.max(rOff, getRight(i) - other.getLeft(i) + NODE_HORIZONTAL_SPACING);
			}
			for (int i = 0; i < other.getDepth(); i++) {
				if (i < getDepth()) {
					widths.get(i).right = other.getRight(i) + rOff;
				} else {
					widths.add(new Width(other.getLeft(i) + rOff, other.getRight(i) + rOff));
				}
			}
			for (Node node : other.nodes) {
				node.x += rOff;
				nodes.add(node);
			}
		}

		private static class Width {
			private int left, right;

			public Width(int left, int right) {
				this.left = left;
				this.right = right;
			}
		}
	}
}
