package io.github.mango_clark.emirecipeforest.test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Loads production forest code against small deterministic EMI client doubles. */
final class IsolatedEmiRuntime implements AutoCloseable {
    private final Path directory;
    private final URLClassLoader loader;

    IsolatedEmiRuntime() throws Exception {
        directory = Files.createTempDirectory("recipeforest-test-runtime");
        Path sources = directory.resolve("sources");
        Path classes = directory.resolve("classes");
        Files.createDirectories(sources);
        Files.createDirectories(classes);
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources().entrySet()) {
            Path file = sources.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file);
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null)) {
            var units = manager.getJavaFileObjectsFromPaths(files);
            boolean success = compiler.getTask(null, manager, null, List.of("-d", classes.toString()), null, units).call();
            if (!success) {
                throw new IllegalStateException("Could not compile isolated EMI test doubles");
            }
        }
        Path mainClasses = Path.of(System.getProperty("user.dir"), "build", "classes", "java", "main");
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL(), mainClasses.toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
    }

    Class<?> type(String name) throws ClassNotFoundException {
        return loader.loadClass(name);
    }

    Object stack(String key, long amount) throws Exception {
        return type("dev.emi.emi.api.stack.EmiStack").getConstructor(String.class, long.class)
                .newInstance(key, amount);
    }

    Object recipe(String id, Object output, List<?> inputs) throws Exception {
        return type("dev.emi.emi.api.recipe.TestRecipe")
                .getConstructor(String.class, type("dev.emi.emi.api.stack.EmiStack"), List.class)
                .newInstance(id, output, inputs);
    }

    @Override
    public void close() throws Exception {
        loader.close();
        delete(directory);
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path child : paths.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) {
                Files.deleteIfExists(child);
            }
        }
    }

    private static Map<String, String> sources() {
        return Map.ofEntries(
            Map.entry("net.minecraft.resources.ResourceLocation", """
                package net.minecraft.resources;
                public record ResourceLocation(String value) { public String toString() { return value; } }
                """),
            Map.entry("net.minecraft.client.gui.GuiGraphics", """
                package net.minecraft.client.gui; public class GuiGraphics {}
                """),
            Map.entry("net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent", """
                package net.minecraft.client.gui.screens.inventory.tooltip;
                public class ClientTooltipComponent { public static ClientTooltipComponent create(Object text) { return new ClientTooltipComponent(); } }
                """),
            Map.entry("net.minecraft.network.chat.Component", """
                package net.minecraft.network.chat;
                public class Component { public static MutableComponent translatable(String key, Object... args) { return new MutableComponent(); } public Object getVisualOrderText() { return this; } }
                """),
            Map.entry("net.minecraft.network.chat.MutableComponent", """
                package net.minecraft.network.chat; public class MutableComponent extends Component {}
                """),
            Map.entry("net.minecraft.world.level.ItemLike", """
                package net.minecraft.world.level; public interface ItemLike {}
                """),
            Map.entry("net.minecraft.world.item.Item", """
                package net.minecraft.world.item; public final class Item implements net.minecraft.world.level.ItemLike { private final String id; public Item(String id){this.id=id;} public String toString(){return id;} }
                """),
            Map.entry("net.minecraft.world.item.Items", """
                package net.minecraft.world.item; public final class Items { public static final Item COMPASS = new Item("compass"); public static final Item OAK_SAPLING = new Item("oak_sapling"); }
                """),
            Map.entry("net.minecraft.client.Minecraft", """
                package net.minecraft.client;
                public final class Minecraft { private static final Minecraft INSTANCE = new Minecraft(); public java.io.File gameDirectory = new java.io.File("."); public static Minecraft getInstance() { return INSTANCE; } }
                """),
            Map.entry("com.google.gson.JsonElement", """
                package com.google.gson;
                public class JsonElement { public boolean isJsonObject(){return this instanceof JsonObject;} public boolean isJsonArray(){return this instanceof JsonArray;} public boolean isJsonPrimitive(){return this instanceof JsonPrimitive;} public JsonObject getAsJsonObject(){return (JsonObject)this;} public JsonArray getAsJsonArray(){return (JsonArray)this;} public String getAsString(){return ((JsonPrimitive)this).getAsString();} public int getAsInt(){return Integer.parseInt(getAsString());} public long getAsLong(){return Long.parseLong(getAsString());} public boolean getAsBoolean(){return Boolean.parseBoolean(getAsString());} public JsonElement deepCopy(){return this;} }
                """),
            Map.entry("com.google.gson.JsonPrimitive", """
                package com.google.gson;
                public final class JsonPrimitive extends JsonElement { private final String value; public JsonPrimitive(Object value){this.value=String.valueOf(value);} public String getAsString(){return value;} }
                """),
            Map.entry("com.google.gson.JsonArray", """
                package com.google.gson;
                public final class JsonArray extends JsonElement implements Iterable<JsonElement> { private final java.util.List<JsonElement> values=new java.util.ArrayList<>(); public void add(JsonElement value){values.add(value);} public void add(String value){add(new JsonPrimitive(value));} public int size(){return values.size();} public JsonElement get(int i){return values.get(i);} public java.util.Iterator<JsonElement> iterator(){return values.iterator();} }
                """),
            Map.entry("com.google.gson.JsonObject", """
                package com.google.gson;
                public final class JsonObject extends JsonElement { private final java.util.Map<String,JsonElement> values=new java.util.LinkedHashMap<>(); public void add(String key,JsonElement value){values.put(key,value);} public void addProperty(String key,String value){add(key,new JsonPrimitive(value));} public void addProperty(String key,Number value){add(key,new JsonPrimitive(value));} public void addProperty(String key,Boolean value){add(key,new JsonPrimitive(value));} public boolean has(String key){return values.containsKey(key);} public JsonElement get(String key){return values.get(key);} public JsonObject getAsJsonObject(String key){return (JsonObject)get(key);} public JsonArray getAsJsonArray(String key){return (JsonArray)get(key);} public java.util.Set<String> keySet(){return values.keySet();} }
                """),
            Map.entry("com.google.gson.Gson", """
                package com.google.gson;
                public class Gson {
                    private static final java.util.Map<String,Object> VALUES = new java.util.HashMap<>();
                    private static int nextId;
                    public <T>T fromJson(String value,Class<T> type){
                        Object result=VALUES.get(value);
                        if(result==null||!type.isInstance(result))throw new IllegalArgumentException("Malformed JSON");
                        return type.cast(result);
                    }
                    public String toJson(Object value){
                        String id="json:"+(++nextId);
                        VALUES.put(id,value);
                        return id;
                    }
                    public String toJson(JsonElement value){return toJson((Object)value);}
                }
                """),
            Map.entry("com.google.gson.GsonBuilder", """
                package com.google.gson; public class GsonBuilder { public GsonBuilder setPrettyPrinting(){return this;} public Gson create(){return new Gson();} }
                """),
            Map.entry("dev.emi.emi.api.stack.EmiIngredient", """
                package dev.emi.emi.api.stack;
                public interface EmiIngredient { java.util.List<EmiStack> getEmiStacks(); EmiIngredient copy(); long getAmount(); EmiIngredient setAmount(long amount); float getChance(); EmiIngredient setChance(float chance); default boolean isEmpty(){return getEmiStacks().isEmpty();} default void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d,int f){} default java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> getTooltip(){return java.util.List.of();} }
                """),
            Map.entry("dev.emi.emi.api.stack.EmiStack", """
                package dev.emi.emi.api.stack;
                public class EmiStack implements EmiIngredient { public static final EmiStack EMPTY=new EmiStack("empty",0); private final String key; private long amount; private float chance=1; public EmiStack(String key,long amount){this.key=key;this.amount=amount;} public static EmiStack of(Object value){return new EmiStack(String.valueOf(System.identityHashCode(value)),1);} public static EmiStack of(net.minecraft.world.level.ItemLike value){return of((Object)value);} public java.util.List<EmiStack> getEmiStacks(){return isEmpty()?java.util.List.of():java.util.List.of(this);} public EmiStack copy(){return new EmiStack(key,amount).setChance(chance);} public long getAmount(){return amount;} public EmiStack setAmount(long value){amount=value;return this;} public float getChance(){return chance;} public EmiStack setChance(float value){chance=value;return this;} public boolean isEmpty(){return amount<=0||key.equals("empty");} public boolean equals(Object other){return other instanceof EmiStack s&&key.equals(s.key);} public int hashCode(){return key.hashCode();} public String toString(){return key+"x"+amount;} }
                """),
            Map.entry("dev.emi.emi.api.recipe.EmiRecipe", """
                package dev.emi.emi.api.recipe;
                public interface EmiRecipe { net.minecraft.resources.ResourceLocation getId(); java.util.List<dev.emi.emi.api.stack.EmiIngredient> getInputs(); java.util.List<dev.emi.emi.api.stack.EmiStack> getOutputs(); default boolean supportsRecipeTree(){return true;} }
                """),
            Map.entry("dev.emi.emi.api.recipe.TestRecipe", """
                package dev.emi.emi.api.recipe;
                public final class TestRecipe implements EmiRecipe { private final net.minecraft.resources.ResourceLocation id; private final dev.emi.emi.api.stack.EmiStack output; private final java.util.List<dev.emi.emi.api.stack.EmiIngredient> inputs; public TestRecipe(String id,dev.emi.emi.api.stack.EmiStack output,java.util.List<dev.emi.emi.api.stack.EmiIngredient> inputs){this.id=new net.minecraft.resources.ResourceLocation(id);this.output=output;this.inputs=inputs;} public net.minecraft.resources.ResourceLocation getId(){return id;} public java.util.List<dev.emi.emi.api.stack.EmiIngredient> getInputs(){return inputs;} public java.util.List<dev.emi.emi.api.stack.EmiStack> getOutputs(){return java.util.List.of(output);} }
                """),
            Map.entry("dev.emi.emi.api.recipe.EmiResolutionRecipe", """
                package dev.emi.emi.api.recipe;
                public final class EmiResolutionRecipe implements EmiRecipe { public final dev.emi.emi.api.stack.EmiIngredient ingredient; public final dev.emi.emi.api.stack.EmiStack stack; public EmiResolutionRecipe(dev.emi.emi.api.stack.EmiIngredient ingredient,dev.emi.emi.api.stack.EmiStack stack){this.ingredient=ingredient;this.stack=stack;} public net.minecraft.resources.ResourceLocation getId(){return null;} public java.util.List<dev.emi.emi.api.stack.EmiIngredient> getInputs(){return java.util.List.of(stack);} public java.util.List<dev.emi.emi.api.stack.EmiStack> getOutputs(){return java.util.List.of(stack);} }
                """),
            Map.entry("dev.emi.emi.api.recipe.EmiPlayerInventory", """
                package dev.emi.emi.api.recipe;
                public final class EmiPlayerInventory { public java.util.Map<dev.emi.emi.api.stack.EmiStack,dev.emi.emi.api.stack.EmiStack> inventory=new java.util.LinkedHashMap<>(); public EmiPlayerInventory(java.util.List<dev.emi.emi.api.stack.EmiStack> stacks){for(var stack:stacks)inventory.put(stack,stack);} }
                """),
            Map.entry("dev.emi.emi.bom.ProgressState", """
                package dev.emi.emi.bom; public enum ProgressState { UNSTARTED, PARTIAL, COMPLETED }
                """),
            Map.entry("dev.emi.emi.bom.FoldState", """
                package dev.emi.emi.bom; public enum FoldState { DEFAULT, EXPANDED, COLLAPSED }
                """),
            Map.entry("dev.emi.emi.bom.FlatMaterialCost", """
                package dev.emi.emi.bom; public class FlatMaterialCost { public dev.emi.emi.api.stack.EmiIngredient ingredient; public long amount; public FlatMaterialCost(dev.emi.emi.api.stack.EmiIngredient ingredient,long amount){this.ingredient=ingredient;this.amount=amount;} }
                """),
            Map.entry("dev.emi.emi.bom.ChanceMaterialCost", """
                package dev.emi.emi.bom; public final class ChanceMaterialCost extends FlatMaterialCost { public long minBatch; public float chance; public ChanceMaterialCost(dev.emi.emi.api.stack.EmiIngredient ingredient,long amount,float chance){super(ingredient,amount);this.chance=chance;} public void merge(long amount,float chance){this.amount+=amount;this.chance=chance;} public void minBatch(long value){minBatch=Math.max(minBatch,value);} }
                """),
            Map.entry("dev.emi.emi.bom.TreeCost", """
                package dev.emi.emi.bom; public final class TreeCost { public java.util.Map<dev.emi.emi.api.stack.EmiIngredient,FlatMaterialCost> costs=new java.util.LinkedHashMap<>(); public java.util.Map<dev.emi.emi.api.stack.EmiIngredient,ChanceMaterialCost> chanceCosts=new java.util.LinkedHashMap<>(); public java.util.Map<dev.emi.emi.api.stack.EmiStack,FlatMaterialCost> remainders=new java.util.LinkedHashMap<>(); public java.util.Map<dev.emi.emi.api.stack.EmiStack,ChanceMaterialCost> chanceRemainders=new java.util.LinkedHashMap<>(); }
                """),
            Map.entry("dev.emi.emi.bom.MaterialNode", """
                package dev.emi.emi.bom;
                public class MaterialNode { public final dev.emi.emi.api.stack.EmiIngredient ingredient; public dev.emi.emi.api.stack.EmiStack remainder=dev.emi.emi.api.stack.EmiStack.EMPTY; public dev.emi.emi.api.recipe.EmiRecipe recipe; public java.util.List<MaterialNode> children=new java.util.ArrayList<>(); public float consumeChance=1,produceChance=1; public long amount,divisor=1,remainderAmount; public boolean catalyst; public FoldState state=FoldState.DEFAULT; public ProgressState progress=ProgressState.UNSTARTED; public long neededBatches,totalNeeded; public MaterialNode(dev.emi.emi.api.stack.EmiIngredient ingredient){this.ingredient=ingredient;this.amount=ingredient.getAmount();} public void defineRecipe(dev.emi.emi.api.recipe.EmiRecipe recipe){this.recipe=recipe;children=new java.util.ArrayList<>();if(recipe!=null){var output=recipe.getOutputs().get(0);divisor=Math.max(1,output.getAmount());produceChance=output.getChance();for(var input:recipe.getInputs()){var child=new MaterialNode(input);child.consumeChance=input.getChance();children.add(child);}}} public void recalculate(MaterialTree tree){} }
                """),
            Map.entry("dev.emi.emi.bom.MaterialTree", """
                package dev.emi.emi.bom;
                public class MaterialTree { public MaterialNode goal; public TreeCost cost=new TreeCost(); public java.util.Map<dev.emi.emi.api.stack.EmiIngredient,dev.emi.emi.api.recipe.EmiRecipe> resolutions=new java.util.LinkedHashMap<>(); public long batches=1; public MaterialTree(dev.emi.emi.api.recipe.EmiRecipe recipe){goal=new MaterialNode(recipe.getOutputs().get(0));goal.defineRecipe(recipe);} public void addResolution(dev.emi.emi.api.stack.EmiIngredient ingredient,dev.emi.emi.api.recipe.EmiRecipe recipe){resolutions.put(ingredient,recipe);} public void recalculate(){} }
                """),
            Map.entry("dev.emi.emi.bom.BoM", """
                package dev.emi.emi.bom; public final class BoM { public static MaterialTree tree; public static boolean craftingMode; public static void setGoal(dev.emi.emi.api.recipe.EmiRecipe recipe){tree=new MaterialTree(recipe);craftingMode=false;} }
                """),
            Map.entry("io.github.mango_clark.emirecipeforest.compat.EmiCompatibility", """
                package io.github.mango_clark.emirecipeforest.compat; public final class EmiCompatibility { public static boolean isCatalyst(dev.emi.emi.bom.MaterialNode node){return node.catalyst;} }
                """),
            Map.entry("dev.emi.emi.EmiPort", """
                package dev.emi.emi; public final class EmiPort { public static net.minecraft.resources.ResourceLocation id(String id){return new net.minecraft.resources.ResourceLocation(id);} }
                """),
            Map.entry("dev.emi.emi.api.EmiApi", """
                package dev.emi.emi.api; public final class EmiApi { private static final dev.emi.emi.api.recipe.EmiRecipeManager MANAGER=new dev.emi.emi.api.recipe.TestRecipeManager(); public static dev.emi.emi.api.recipe.EmiRecipeManager getRecipeManager(){return MANAGER;} }
                """),
            Map.entry("dev.emi.emi.api.recipe.EmiRecipeManager", """
                package dev.emi.emi.api.recipe; public interface EmiRecipeManager { EmiRecipe getRecipe(net.minecraft.resources.ResourceLocation id); }
                """),
            Map.entry("dev.emi.emi.api.recipe.TestRecipeManager", """
                package dev.emi.emi.api.recipe; public final class TestRecipeManager implements EmiRecipeManager { private final java.util.Map<String,EmiRecipe> recipes=new java.util.HashMap<>(); public EmiRecipe getRecipe(net.minecraft.resources.ResourceLocation id){return recipes.get(id.toString());} public void put(EmiRecipe recipe){recipes.put(recipe.getId().toString(),recipe);} }
                """),
            Map.entry("dev.emi.emi.api.stack.serializer.EmiIngredientSerializer", """
                package dev.emi.emi.api.stack.serializer; public final class EmiIngredientSerializer { public static com.google.gson.JsonElement getSerialized(dev.emi.emi.api.stack.EmiIngredient value){var object=new com.google.gson.JsonObject();object.addProperty("key",value.getEmiStacks().get(0).toString());return object;} public static dev.emi.emi.api.stack.EmiIngredient getDeserialized(com.google.gson.JsonElement value){return dev.emi.emi.api.stack.EmiStack.EMPTY;} }
                """),
            Map.entry("dev.emi.emi.screen.EmiScreenManager", """
                package dev.emi.emi.screen; public final class EmiScreenManager { public static final Search search=new Search(); public static final class Search { public void setValue(String value){} } }
                """),
            Map.entry("org.slf4j.Logger", """
                package org.slf4j; public interface Logger { default void warn(String message,Object arg){} default void warn(String message,Throwable error){} default void error(String message,Object first,Object second){} }
                """),
            Map.entry("io.github.mango_clark.emirecipeforest.Constants", """
                package io.github.mango_clark.emirecipeforest; public final class Constants { public static final org.slf4j.Logger LOG=new org.slf4j.Logger(){}; }
                """)
        );
    }
}
