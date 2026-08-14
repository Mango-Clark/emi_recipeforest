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
    private static final int SCHEMA_VERSION = 2;
    private static final int LEGACY_SCHEMA_VERSION = 1;
    /** Default number of live Forest roots. */
    public static final int DEFAULT_MAX_ROOTS = 64;
    /** Upper bound accepted by the maximum-root setting. */
    public static final int MAX_ROOTS_LIMIT = 256;
    /** Maximum number of persisted inputs for the Forest action. */
    public static final int MAX_FOREST_BINDINGS = 4;
    /** EMI modifier mask used by the default {@code Shift+F} binding. */
    public static final int SHIFT_MODIFIER = 4;
    /** GLFW key code for F, retained for migration and source compatibility. */
    public static final int DEFAULT_FOREST_KEY_CODE = 70;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<SearchBookmark> SEARCHES = new ArrayList<>();
    private static final List<TreeBookmark> TREES = new ArrayList<>();
    private static int rootGridColumns = 8;
    private static int rootGridRows = 2;
    private static int maxRoots = DEFAULT_MAX_ROOTS;
    private static ResolutionScope resolutionScope = ResolutionScope.ALL_ROOTS;
    private static RootLayout rootLayout = RootLayout.LIST;
    private static QuantityMode quantityMode = QuantityMode.ICON;
    private static List<ForestBinding> forestBindings = defaultForestBindings();
    private static boolean boxEnabled = true;
    private static int stacksPerBox = 27;

    /** Controls which roots receive an EMI recipe resolution. */
    public enum ResolutionScope {
        /** Apply to every live root. */
        ALL_ROOTS,
        /** Apply only to roots containing the resolved ingredient. */
        MATCHING_ROOTS,
        /** Apply only to the selected root. */
        SELECTED_ROOT
    }

    /** Available root-panel layouts. */
    public enum RootLayout {
        /** Vertically scrolling detailed root rows. */
        LIST,
        /** Paged compact root cells. */
        GRID
    }

    /** Available quantity presentation modes. */
    public enum QuantityMode {
        /** Box, stack, and item icons. */
        ICON,
        /** Compact textual quantities. */
        TEXT
    }

    /** Persisted input device categories understood by Minecraft. */
    public enum BindingType {
        /** Layout-aware key symbol. */
        KEYSYM,
        /** Physical keyboard scan code. */
        SCANCODE,
        /** Mouse button. */
        MOUSE
    }

    /**
     * Loader-neutral representation accepted by EMI's ModifiedKey adapter.
     *
     * @param type input device category
     * @param name stable input name
     * @param value GLFW or mouse code
     * @param modifiers EMI modifier mask
     */
    public record ForestBinding(BindingType type, String name, int value, int modifiers) {
        /** Validates and normalizes a serialized binding. */
        public ForestBinding {
            Objects.requireNonNull(type, "type");
            name = normalize(name);
            if (name.isEmpty() || value < 0 || (modifiers & ~7) != 0) {
                throw new IllegalArgumentException("Invalid RecipeForest binding");
            }
        }
    }

    private ForestBookmarks() {
    }

    /** Returns all addon-owned cards in sidebar order.
     * @return immutable combined search and tree bookmark cards
     */
    public static List<EmiIngredient> cards() {
        List<EmiIngredient> cards = new ArrayList<>(SEARCHES.size() + TREES.size());
        cards.addAll(SEARCHES);
        cards.addAll(TREES);
        return List.copyOf(cards);
    }

    /** Returns persisted search cards.
     * @return immutable search bookmark list
     */
    public static List<SearchBookmark> searches() {
        return List.copyOf(SEARCHES);
    }

    /** Returns persisted tree cards.
     * @return immutable tree bookmark list
     */
    public static List<TreeBookmark> trees() {
        return List.copyOf(TREES);
    }

    /** Returns the root-grid width.
     * @return configured column count
     */
    public static int getRootGridColumns() {
        return rootGridColumns;
    }

    /** Returns the root-grid height.
     * @return configured row count
     */
    public static int getRootGridRows() {
        return rootGridRows;
    }

    /**
     * Persists a clamped root-grid size.
     *
     * @param columns requested columns
     * @param rows requested rows
     */
    public static void setRootGridSize(int columns, int rows) {
        rootGridColumns = clamp(columns, 1, 16);
        rootGridRows = clamp(rows, 1, 8);
        save();
    }

    /** Returns the configured maximum number of live Forest roots.
     * @return maximum root count
     */
    public static int getMaxRoots() {
        return maxRoots;
    }

    /** Persists a bounded maximum root count.
     * @param roots requested maximum root count
     */
    public static void setMaxRoots(int roots) {
        maxRoots = clamp(Math.max(roots, ForestManager.size()), 1, MAX_ROOTS_LIMIT);
        save();
    }

    /** Returns the default resolution scope.
     * @return persisted resolution scope
     */
    public static ResolutionScope getResolutionScope() {
        return resolutionScope;
    }

    /** Persists the default resolution scope.
     * @param scope non-null scope
     */
    public static void setResolutionScope(ResolutionScope scope) {
        resolutionScope = Objects.requireNonNull(scope, "scope");
        save();
    }

    /** Returns the root-panel layout.
     * @return persisted root layout
     */
    public static RootLayout getRootLayout() {
        return rootLayout;
    }

    /** Persists the root-panel layout.
     * @param layout non-null layout
     */
    public static void setRootLayout(RootLayout layout) {
        rootLayout = Objects.requireNonNull(layout, "layout");
        save();
    }

    /** Returns the quantity presentation.
     * @return persisted quantity mode
     */
    public static QuantityMode getQuantityMode() {
        return quantityMode;
    }

    /** Persists the quantity presentation.
     * @param mode non-null mode
     */
    public static void setQuantityMode(QuantityMode mode) {
        quantityMode = Objects.requireNonNull(mode, "mode");
        save();
    }

    /** Returns the Forest action inputs.
     * @return immutable persisted bindings
     */
    public static List<ForestBinding> getForestBindings() {
        return List.copyOf(forestBindings);
    }

    /**
     * Normalizes, limits, and persists Forest input bindings.
     *
     * @param bindings bindings to persist
     */
    public static void setForestBindings(List<ForestBinding> bindings) {
        Objects.requireNonNull(bindings, "bindings");
        List<ForestBinding> validated = new ArrayList<>(Math.min(bindings.size(), MAX_FOREST_BINDINGS));
        for (ForestBinding binding : bindings) {
            if (binding != null && validated.size() < MAX_FOREST_BINDINGS && !validated.contains(binding)) {
                validated.add(binding);
            }
        }
        forestBindings = List.copyOf(validated);
        save();
    }

    /** Restores and persists the default Forest inputs. */
    public static void resetForestBindings() {
        forestBindings = defaultForestBindings();
        save();
    }

    /**
     * Temporary compatibility for callers replaced by the native EMI bind adapter in the next wave.
     *
     * @return first persisted keysym, or the legacy default
     */
    @Deprecated
    public static int getForestKeyCode() {
        return forestBindings.stream().filter(binding -> binding.type == BindingType.KEYSYM)
                .mapToInt(ForestBinding::value).findFirst().orElse(DEFAULT_FOREST_KEY_CODE);
    }

    /**
     * Temporary compatibility for callers replaced by the native EMI bind adapter in the next wave.
     *
     * @param keyCode legacy GLFW key code
     */
    @Deprecated
    public static void setForestKeyCode(int keyCode) {
        forestBindings = migratedBindings(keyCode);
        save();
    }

    /** Reports whether box grouping is enabled.
     * @return whether complete stacks are grouped into boxes
     */
    public static boolean isBoxEnabled() {
        return boxEnabled;
    }

    /** Persists the box-grouping toggle.
     * @param enabled whether box grouping is enabled
     */
    public static void setBoxEnabled(boolean enabled) {
        boxEnabled = enabled;
        save();
    }

    /** Returns the box capacity in stacks.
     * @return configured stacks per box
     */
    public static int getStacksPerBox() {
        return stacksPerBox;
    }

    /** Persists a clamped box capacity.
     * @param stacks requested stacks per box
     */
    public static void setStacksPerBox(int stacks) {
        stacksPerBox = clamp(stacks, 1, 256);
        save();
    }

    /**
     * Adds a normalized search bookmark unless an equivalent one already exists.
     *
     * @param query search text
     * @return new or existing bookmark, or {@code null} for an empty query
     */
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

    /**
     * Captures and persists the current forest.
     *
     * @param name bookmark name
     * @return captured bookmark, or {@code null} when the forest has no serializable roots
     */
    public static TreeBookmark addTree(String name) {
        TreeBookmark bookmark = TreeBookmark.capture(normalizeName(name));
        if (bookmark.roots.isEmpty()) {
            return null;
        }
        TREES.add(bookmark);
        save();
        return bookmark;
    }

    /**
     * Callback target for a screen or mixin that owns the text-entry UI.
     *
     * @return callback that captures the current forest under the supplied name
     */
    public static Consumer<String> saveCurrentTreeCallback() {
        return ForestBookmarks::addTree;
    }

    /**
     * Removes and persists an addon-owned bookmark card.
     *
     * @param card bookmark card
     * @return whether a card was removed
     */
    public static boolean remove(EmiIngredient card) {
        boolean removed = SEARCHES.remove(card) | TREES.remove(card);
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * Renames an owned tree bookmark.
     *
     * @param bookmark bookmark to rename
     * @param name replacement name
     * @return whether the bookmark is still owned
     */
    public static boolean rename(TreeBookmark bookmark, String name) {
        if (!TREES.contains(bookmark)) {
            return false;
        }
        bookmark.name = normalizeName(name);
        save();
        return true;
    }

    /**
     * Applies a search card or restores a tree card to live state.
     *
     * @param card bookmark card
     * @return whether the card type was supported and restoration succeeded
     */
    public static boolean apply(EmiIngredient card) {
        if (card instanceof SearchBookmark search) {
            search.apply();
            return true;
        }
        return card instanceof TreeBookmark tree && tree.apply();
    }

    /** Loads addon configuration and bookmark cards, resetting to defaults on missing data. */
    public static void load() {
        SEARCHES.clear();
        TREES.clear();
        rootGridColumns = 8;
        rootGridRows = 2;
        maxRoots = DEFAULT_MAX_ROOTS;
        resolutionScope = ResolutionScope.ALL_ROOTS;
        rootLayout = RootLayout.LIST;
        quantityMode = QuantityMode.ICON;
        forestBindings = defaultForestBindings();
        boxEnabled = true;
        stacksPerBox = 27;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            int schema = root == null ? -1 : intValue(root, "schema", -1);
            boolean migrateLegacyBindings = false;
            if (root == null || (schema != LEGACY_SCHEMA_VERSION && schema != SCHEMA_VERSION)) {
                Constants.LOG.warn("Ignoring unsupported RecipeForest bookmark schema in {}", path);
                return;
            }
            if (root.has("settings") && root.get("settings").isJsonObject()) {
                JsonObject settings = root.getAsJsonObject("settings");
                rootGridColumns = clamp(intValue(settings, "rootGridColumns", 8), 1, 16);
                rootGridRows = clamp(intValue(settings, "rootGridRows", 2), 1, 8);
                maxRoots = clamp(intValue(settings, "maxRoots", DEFAULT_MAX_ROOTS), 1, MAX_ROOTS_LIMIT);
                resolutionScope = enumValue(settings, "resolutionScope", ResolutionScope.class,
                        ResolutionScope.ALL_ROOTS);
                rootLayout = enumValue(settings, "rootLayout", RootLayout.class, RootLayout.LIST);
                quantityMode = enumValue(settings, "quantityMode", QuantityMode.class, QuantityMode.ICON);
                forestBindings = readForestBindings(settings);
                migrateLegacyBindings = schema == LEGACY_SCHEMA_VERSION && !settings.has("forestBindings")
                        && validLegacyKeyCode(settings);
                boxEnabled = booleanValue(settings, "boxEnabled", true);
                stacksPerBox = clamp(intValue(settings, "stacksPerBox", 27), 1, 256);
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
            if (migrateLegacyBindings) {
                save();
            }
        } catch (Exception exception) {
            Constants.LOG.error("Could not load RecipeForest bookmarks from {}", path, exception);
        }
    }

    /** Persists addon configuration and bookmarks with an atomic replacement when supported. */
    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonObject settings = new JsonObject();
        settings.addProperty("rootGridColumns", rootGridColumns);
        settings.addProperty("rootGridRows", rootGridRows);
        settings.addProperty("maxRoots", maxRoots);
        settings.addProperty("resolutionScope", resolutionScope.name());
        settings.addProperty("rootLayout", rootLayout.name());
        settings.addProperty("quantityMode", quantityMode.name());
        JsonArray bindings = new JsonArray();
        for (ForestBinding binding : forestBindings) {
            JsonObject object = new JsonObject();
            object.addProperty("type", binding.type.name());
            object.addProperty("name", binding.name);
            object.addProperty("value", binding.value);
            object.addProperty("modifiers", binding.modifiers);
            bindings.add(object);
        }
        settings.add("forestBindings", bindings);
        settings.addProperty("boxEnabled", boxEnabled);
        settings.addProperty("stacksPerBox", stacksPerBox);
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

    private static List<ForestBinding> readForestBindings(JsonObject settings) {
        if (!settings.has("forestBindings")) {
            return validLegacyKeyCode(settings)
                    ? migratedBindings(intValue(settings, "forestKeyCode", DEFAULT_FOREST_KEY_CODE))
                    : defaultForestBindings();
        }
        if (!settings.get("forestBindings").isJsonArray()) {
            return defaultForestBindings();
        }
        JsonArray serialized = settings.getAsJsonArray("forestBindings");
        List<ForestBinding> bindings = new ArrayList<>(Math.min(serialized.size(), MAX_FOREST_BINDINGS));
        for (JsonElement element : serialized) {
            if (bindings.size() >= MAX_FOREST_BINDINGS) {
                break;
            }
            try {
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    ForestBinding binding = new ForestBinding(
                            enumValue(object, "type", BindingType.class, null),
                            object.has("name") ? object.get("name").getAsString() : "",
                            intValue(object, "value", -1), intValue(object, "modifiers", -1));
                    if (!bindings.contains(binding)) {
                        bindings.add(binding);
                    }
                }
            } catch (RuntimeException exception) {
                Constants.LOG.warn("Skipping malformed RecipeForest binding", exception);
            }
        }
        return serialized.size() == 0 || !bindings.isEmpty() ? List.copyOf(bindings) : defaultForestBindings();
    }

    private static boolean validLegacyKeyCode(JsonObject settings) {
        try {
            return settings.has("forestKeyCode") && settings.get("forestKeyCode").isJsonPrimitive()
                    && settings.get("forestKeyCode").getAsInt() >= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static List<ForestBinding> defaultForestBindings() {
        return List.of(new ForestBinding(BindingType.KEYSYM, "key.keyboard.f", DEFAULT_FOREST_KEY_CODE, 0));
    }

    private static List<ForestBinding> migratedBindings(int keyCode) {
        if (keyCode < 0) {
            return defaultForestBindings();
        }
        String name = keyCode == DEFAULT_FOREST_KEY_CODE ? "key.keyboard.f" : "key.keyboard." + keyCode;
        return List.of(new ForestBinding(BindingType.KEYSYM, name, keyCode, 0),
                new ForestBinding(BindingType.KEYSYM, name, keyCode, SHIFT_MODIFIER));
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) {
                return fallback;
            }
            String value = element.getAsString();
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            return fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumValue(JsonObject object, String key, Class<T> type, T fallback) {
        try {
            return object.has(key) ? Enum.valueOf(type, object.get(key).getAsString()) : fallback;
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

    /** EMI ingredient card that restores a saved search query. */
    public static final class SearchBookmark implements EmiIngredient {
        private static final EmiStack ICON = EmiStack.of(Items.COMPASS);
        private final String query;

        private SearchBookmark(String query) {
            this.query = query;
        }

        /** @return saved search text */
        public String query() {
            return query;
        }

        /** Replaces EMI's current search text with this bookmark. */
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

    /** EMI ingredient card containing a reload-safe serialized forest snapshot. */
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

        /** @return current bookmark name */
        public String name() {
            return name;
        }

        /**
         * Restores every still-valid root from current EMI recipe objects.
         *
         * @return whether at least one root was restored
         */
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
            for (PreparedRoot preparedRoot : prepared.subList(0, Math.min(prepared.size(), getMaxRoots()))) {
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
