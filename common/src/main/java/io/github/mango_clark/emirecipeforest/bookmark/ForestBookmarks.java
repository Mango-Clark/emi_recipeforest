package io.github.mango_clark.emirecipeforest.bookmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.bom.FoldState;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.screen.EmiScreenManager;
import io.github.mango_clark.emirecipeforest.Constants;
import io.github.mango_clark.emirecipeforest.forest.ForestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Addon-owned search and forest bookmarks, independent of EMI's emi.json. */
public final class ForestBookmarks {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<SearchBookmark> SEARCHES = new ArrayList<>();
    private static final List<TreeBookmark> TREES = new ArrayList<>();
    private static int rootGridColumns = 8;
    private static int rootGridRows = 2;

    private ForestBookmarks() {
    }

    public static List<EmiIngredient> cards() {
        List<EmiIngredient> cards = new ArrayList<>(SEARCHES.size() + TREES.size());
        cards.addAll(SEARCHES);
        cards.addAll(TREES);
        return List.copyOf(cards);
    }

    public static List<SearchBookmark> searches() {
        return List.copyOf(SEARCHES);
    }

    public static List<TreeBookmark> trees() {
        return List.copyOf(TREES);
    }

    public static int getRootGridColumns() {
        return rootGridColumns;
    }

    public static int getRootGridRows() {
        return rootGridRows;
    }

    public static void setRootGridSize(int columns, int rows) {
        rootGridColumns = clamp(columns, 1, 16);
        rootGridRows = clamp(rows, 1, 8);
        save();
    }

    public static SearchBookmark addSearch(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return null;
        }
        for (SearchBookmark bookmark : SEARCHES) {
            if (bookmark.query.equals(normalized)) {
                return bookmark;
            }
        }
        SearchBookmark bookmark = new SearchBookmark(normalized);
        SEARCHES.add(bookmark);
        save();
        return bookmark;
    }

    public static TreeBookmark addTree(String name) {
        TreeBookmark bookmark = TreeBookmark.capture(normalizeName(name));
        if (bookmark.roots.isEmpty()) {
            return null;
        }
        TREES.add(bookmark);
        save();
        return bookmark;
    }

    /** Callback target for a screen or mixin that owns the text-entry UI. */
    public static Consumer<String> saveCurrentTreeCallback() {
        return ForestBookmarks::addTree;
    }

    public static boolean remove(EmiIngredient card) {
        boolean removed = SEARCHES.remove(card) | TREES.remove(card);
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean rename(TreeBookmark bookmark, String name) {
        if (!TREES.contains(bookmark)) {
            return false;
        }
        bookmark.name = normalizeName(name);
        save();
        return true;
    }

    public static boolean apply(EmiIngredient card) {
        if (card instanceof SearchBookmark search) {
            search.apply();
            return true;
        }
        return card instanceof TreeBookmark tree && tree.apply();
    }

    public static void load() {
        SEARCHES.clear();
        TREES.clear();
        rootGridColumns = 8;
        rootGridRows = 2;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || intValue(root, "schema", -1) != SCHEMA_VERSION) {
                Constants.LOG.warn("Ignoring unsupported RecipeForest bookmark schema in {}", path);
                return;
            }
            if (root.has("settings") && root.get("settings").isJsonObject()) {
                JsonObject settings = root.getAsJsonObject("settings");
                rootGridColumns = clamp(intValue(settings, "rootGridColumns", 8), 1, 16);
                rootGridRows = clamp(intValue(settings, "rootGridRows", 2), 1, 8);
            }
            JsonArray searches = array(root, "searches");
            for (JsonElement element : searches) {
                try {
                    if (element.isJsonPrimitive()) {
                        String query = normalize(element.getAsString());
                        if (!query.isEmpty() && SEARCHES.stream().noneMatch(bookmark -> bookmark.query.equals(query))) {
                            SEARCHES.add(new SearchBookmark(query));
                        }
                    }
                } catch (RuntimeException exception) {
                    Constants.LOG.warn("Skipping malformed RecipeForest search bookmark", exception);
                }
            }
            for (JsonElement element : array(root, "trees")) {
                try {
                    if (element.isJsonObject()) {
                        TreeBookmark bookmark = TreeBookmark.fromJson(element.getAsJsonObject());
                        if (bookmark != null && !bookmark.roots.isEmpty()) {
                            TREES.add(bookmark);
                        }
                    }
                } catch (RuntimeException exception) {
                    Constants.LOG.warn("Skipping malformed RecipeForest tree bookmark", exception);
                }
            }
        } catch (Exception exception) {
            Constants.LOG.error("Could not load RecipeForest bookmarks from {}", path, exception);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject settings = new JsonObject();
        settings.addProperty("rootGridColumns", rootGridColumns);
        settings.addProperty("rootGridRows", rootGridRows);
        root.add("settings", settings);
        JsonArray searches = new JsonArray();
        for (SearchBookmark bookmark : SEARCHES) {
            searches.add(bookmark.query);
        }
        root.add("searches", searches);
        JsonArray trees = new JsonArray();
        for (TreeBookmark bookmark : TREES) {
            trees.add(bookmark.toJson());
        }
        root.add("trees", trees);

        Path target = path();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Constants.LOG.error("Could not save RecipeForest bookmarks to {}", target, exception);
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("emi_recipeforest.json");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private static String normalizeName(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "Recipe Forest" : normalized;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public static final class SearchBookmark implements EmiIngredient {
        private static final EmiStack ICON = EmiStack.of(Items.COMPASS);
        private final String query;

        private SearchBookmark(String query) {
            this.query = query;
        }

        public String query() {
            return query;
        }

        public void apply() {
            EmiScreenManager.search.setValue(query);
        }

        @Override
        public List<EmiStack> getEmiStacks() {
            return ICON.getEmiStacks();
        }

        @Override
        public EmiIngredient copy() {
            return this;
        }

        @Override
        public long getAmount() {
            return 1;
        }

        @Override
        public EmiIngredient setAmount(long amount) {
            return this;
        }

        @Override
        public float getChance() {
            return 1;
        }

        @Override
        public EmiIngredient setChance(float chance) {
            return this;
        }

        @Override
        public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
            ICON.render(draw, x, y, delta, flags);
        }

        @Override
        public List<ClientTooltipComponent> getTooltip() {
            return List.of(
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.search", query)),
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.apply")),
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.delete")));
        }
    }

    public static final class TreeBookmark implements EmiIngredient {
        private static final EmiStack ICON = EmiStack.of(Items.OAK_SAPLING);
        private String name;
        private final int selectedIndex;
        private final boolean craftingMode;
        private final List<RootSnapshot> roots;

        private TreeBookmark(String name, int selectedIndex, boolean craftingMode, List<RootSnapshot> roots) {
            this.name = name;
            this.selectedIndex = selectedIndex;
            this.craftingMode = craftingMode;
            this.roots = List.copyOf(roots);
        }

        public String name() {
            return name;
        }

        public boolean apply() {
            List<PreparedRoot> prepared = new ArrayList<>();
            try {
                for (RootSnapshot snapshot : roots) {
                    try {
                        EmiRecipe recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(snapshot.recipeId));
                        if (recipe == null || !recipe.supportsRecipeTree()) {
                            continue;
                        }
                        MaterialTree tree = new MaterialTree(recipe);
                        tree.batches = Math.max(1, snapshot.batches);
                        snapshot.restoreResolutions(tree);
                        snapshot.restoreFolds(tree.goal);
                        prepared.add(new PreparedRoot(recipe, tree));
                    } catch (RuntimeException exception) {
                        Constants.LOG.warn("Skipping invalid root '{}' in RecipeForest bookmark '{}'",
                            snapshot.recipeId, name, exception);
                    }
                }
            } catch (RuntimeException exception) {
                Constants.LOG.warn("Could not prepare RecipeForest bookmark '{}'", name, exception);
                return false;
            }
            if (prepared.isEmpty()) {
                return false;
            }

            // No live state is touched until every recoverable root has been fully prepared.
            ForestManager.clear();
            for (PreparedRoot preparedRoot : prepared) {
                MaterialTree tree = ForestManager.add(preparedRoot.recipe);
                tree.batches = preparedRoot.tree.batches;
                tree.resolutions.clear();
                tree.resolutions.putAll(preparedRoot.tree.resolutions);
                tree.recalculate();
                copyFoldStates(preparedRoot.tree.goal, tree.goal);
            }
            ForestManager.select(Math.min(Math.max(0, selectedIndex), ForestManager.size() - 1));
            ForestManager.setCraftingMode(craftingMode);
            return true;
        }

        private static void copyFoldStates(MaterialNode source, MaterialNode target) {
            target.state = source.state;
            if (source.children == null || target.children == null) {
                return;
            }
            int children = Math.min(source.children.size(), target.children.size());
            for (int i = 0; i < children; i++) {
                copyFoldStates(source.children.get(i), target.children.get(i));
            }
        }

        private static TreeBookmark capture(String name) {
            List<RootSnapshot> roots = new ArrayList<>();
            for (MaterialTree tree : ForestManager.getTrees()) {
                RootSnapshot snapshot = RootSnapshot.capture(tree);
                if (snapshot != null) {
                    roots.add(snapshot);
                }
            }
            return new TreeBookmark(name, ForestManager.getSelectedIndex(), ForestManager.isCraftingMode(), roots);
        }

        private static TreeBookmark fromJson(JsonObject object) {
            String name = object.has("name") ? normalizeName(object.get("name").getAsString()) : "Recipe Forest";
            List<RootSnapshot> roots = new ArrayList<>();
            for (JsonElement element : array(object, "roots")) {
                if (element.isJsonObject()) {
                    RootSnapshot snapshot = RootSnapshot.fromJson(element.getAsJsonObject());
                    if (snapshot != null) {
                        roots.add(snapshot);
                    }
                }
            }
            return new TreeBookmark(name, intValue(object, "selected", 0),
                object.has("crafting") && object.get("crafting").getAsBoolean(), roots);
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("name", name);
            object.addProperty("selected", selectedIndex);
            object.addProperty("crafting", craftingMode);
            JsonArray rootsJson = new JsonArray();
            for (RootSnapshot root : roots) {
                rootsJson.add(root.toJson());
            }
            object.add("roots", rootsJson);
            return object;
        }

        @Override
        public List<EmiStack> getEmiStacks() {
            return ICON.getEmiStacks();
        }

        @Override
        public EmiIngredient copy() {
            return this;
        }

        @Override
        public long getAmount() {
            return 1;
        }

        @Override
        public EmiIngredient setAmount(long amount) {
            return this;
        }

        @Override
        public float getChance() {
            return 1;
        }

        @Override
        public EmiIngredient setChance(float chance) {
            return this;
        }

        @Override
        public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
            ICON.render(draw, x, y, delta, flags);
        }

        @Override
        public List<ClientTooltipComponent> getTooltip() {
            return List.of(
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.tree", name, roots.size())),
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.apply")),
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.delete")),
                tooltip(Component.translatable("tooltip.emi_recipeforest.bookmark.rename")));
        }
    }

    private static ClientTooltipComponent tooltip(Component component) {
        return ClientTooltipComponent.create(component.getVisualOrderText());
    }

    private static final class RootSnapshot {
        private final String recipeId;
        private final long batches;
        private final List<ResolutionSnapshot> resolutions;
        private final Map<String, FoldState> folds;

        private RootSnapshot(String recipeId, long batches, List<ResolutionSnapshot> resolutions,
                Map<String, FoldState> folds) {
            this.recipeId = recipeId;
            this.batches = batches;
            this.resolutions = List.copyOf(resolutions);
            this.folds = Map.copyOf(folds);
        }

        private static RootSnapshot capture(MaterialTree tree) {
            if (tree == null || tree.goal == null || tree.goal.recipe == null || tree.goal.recipe.getId() == null) {
                return null;
            }
            List<ResolutionSnapshot> resolutions = new ArrayList<>();
            for (Map.Entry<EmiIngredient, EmiRecipe> entry : tree.resolutions.entrySet()) {
                ResolutionSnapshot snapshot = ResolutionSnapshot.capture(entry.getKey(), entry.getValue());
                if (snapshot != null) {
                    resolutions.add(snapshot);
                }
            }
            Map<String, FoldState> folds = new LinkedHashMap<>();
            captureFolds(tree.goal, "", folds);
            return new RootSnapshot(tree.goal.recipe.getId().toString(), tree.batches, resolutions, folds);
        }

        private static RootSnapshot fromJson(JsonObject object) {
            if (!object.has("recipe")) {
                return null;
            }
            List<ResolutionSnapshot> resolutions = new ArrayList<>();
            for (JsonElement element : array(object, "resolutions")) {
                if (element.isJsonObject()) {
                    ResolutionSnapshot snapshot = ResolutionSnapshot.fromJson(element.getAsJsonObject());
                    if (snapshot != null) {
                        resolutions.add(snapshot);
                    }
                }
            }
            Map<String, FoldState> folds = new LinkedHashMap<>();
            JsonObject foldObject = object.has("folds") && object.get("folds").isJsonObject()
                ? object.getAsJsonObject("folds") : new JsonObject();
            for (String path : foldObject.keySet()) {
                try {
                    folds.put(path, FoldState.valueOf(foldObject.get(path).getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // Unknown future fold state: retain the default.
                }
            }
            return new RootSnapshot(object.get("recipe").getAsString(),
                Math.max(1, longValue(object, "batches", 1)), resolutions, folds);
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("recipe", recipeId);
            object.addProperty("batches", batches);
            JsonArray resolutionJson = new JsonArray();
            for (ResolutionSnapshot resolution : resolutions) {
                resolutionJson.add(resolution.toJson());
            }
            object.add("resolutions", resolutionJson);
            JsonObject foldJson = new JsonObject();
            folds.forEach((path, state) -> foldJson.addProperty(path, state.name()));
            object.add("folds", foldJson);
            return object;
        }

        private void restoreResolutions(MaterialTree tree) {
            for (ResolutionSnapshot snapshot : resolutions) {
                try {
                    snapshot.restore(tree);
                } catch (RuntimeException exception) {
                    Constants.LOG.warn("Skipping malformed resolution in RecipeForest root '{}'", recipeId, exception);
                }
            }
        }

        private void restoreFolds(MaterialNode root) {
            folds.forEach((path, state) -> {
                MaterialNode node = findNode(root, path);
                if (node != null) {
                    node.state = state;
                }
            });
        }

        private static void captureFolds(MaterialNode node, String path, Map<String, FoldState> folds) {
            folds.put(path, node.state);
            if (node.children != null) {
                for (int i = 0; i < node.children.size(); i++) {
                    captureFolds(node.children.get(i), path.isEmpty() ? Integer.toString(i) : path + '/' + i, folds);
                }
            }
        }

        private static MaterialNode findNode(MaterialNode node, String path) {
            if (path.isEmpty()) {
                return node;
            }
            for (String part : path.split("/")) {
                if (node.children == null) {
                    return null;
                }
                try {
                    int index = Integer.parseInt(part);
                    if (index < 0 || index >= node.children.size()) {
                        return null;
                    }
                    node = node.children.get(index);
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
            return node;
        }
    }

    private static final class ResolutionSnapshot {
        private final JsonElement ingredient;
        private final String recipeId;
        private final JsonElement selectedStack;

        private ResolutionSnapshot(JsonElement ingredient, String recipeId, JsonElement selectedStack) {
            this.ingredient = ingredient;
            this.recipeId = recipeId;
            this.selectedStack = selectedStack;
        }

        private static ResolutionSnapshot capture(EmiIngredient ingredient, EmiRecipe recipe) {
            JsonElement ingredientJson = EmiIngredientSerializer.getSerialized(ingredient);
            if (ingredientJson == null) {
                return null;
            }
            if (recipe == null) {
                return new ResolutionSnapshot(ingredientJson, null, null);
            }
            if (recipe instanceof EmiResolutionRecipe resolution) {
                JsonElement stackJson = EmiIngredientSerializer.getSerialized(resolution.stack);
                return stackJson == null ? null : new ResolutionSnapshot(ingredientJson, null, stackJson);
            }
            return recipe.getId() == null ? null
                : new ResolutionSnapshot(ingredientJson, recipe.getId().toString(), null);
        }

        private static ResolutionSnapshot fromJson(JsonObject object) {
            if (!object.has("ingredient")) {
                return null;
            }
            String recipeId = object.has("recipe") ? object.get("recipe").getAsString() : null;
            JsonElement stack = object.has("stack") ? object.get("stack").deepCopy() : null;
            return new ResolutionSnapshot(object.get("ingredient").deepCopy(), recipeId, stack);
        }

        private JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.add("ingredient", ingredient.deepCopy());
            if (recipeId != null) {
                object.addProperty("recipe", recipeId);
            }
            if (selectedStack != null) {
                object.add("stack", selectedStack.deepCopy());
            }
            return object;
        }

        private void restore(MaterialTree tree) {
            EmiIngredient key = EmiIngredientSerializer.getDeserialized(ingredient.deepCopy());
            if (key.isEmpty()) {
                return;
            }
            EmiRecipe recipe = null;
            if (selectedStack != null) {
                EmiIngredient stack = EmiIngredientSerializer.getDeserialized(selectedStack.deepCopy());
                if (!stack.isEmpty() && stack.getEmiStacks().size() == 1) {
                    recipe = new EmiResolutionRecipe(key, stack.getEmiStacks().get(0));
                } else {
                    return;
                }
            } else if (recipeId != null) {
                recipe = EmiApi.getRecipeManager().getRecipe(EmiPort.id(recipeId));
                if (recipe == null) {
                    return;
                }
            }
            tree.addResolution(key, recipe);
        }
    }

    private record PreparedRoot(EmiRecipe recipe, MaterialTree tree) {
        private PreparedRoot {
            Objects.requireNonNull(recipe);
            Objects.requireNonNull(tree);
        }
    }
}
