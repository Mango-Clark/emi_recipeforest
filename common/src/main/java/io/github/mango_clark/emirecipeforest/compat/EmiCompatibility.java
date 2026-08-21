package io.github.mango_clark.emirecipeforest.compat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.TreeCost;
import io.github.mango_clark.emirecipeforest.platform.Services;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Verifies the EMI implementation surface used by RecipeForest mixins. */
public final class EmiCompatibility {
    /** Human-readable EMI version range accepted by this build. */
    public static final String SUPPORTED_RANGE = "1.1.13+1.21.1 through 1.1.24+1.21.1";

    private static final Pattern SUPPORTED_VERSION = Pattern.compile(
            "^1\\.1\\.(\\d+)\\+1\\.21\\.1(?:\\+[0-9A-Za-z][0-9A-Za-z._-]*)*$");
    private static final int MIN_PATCH = 13;
    private static final int MAX_PATCH = 24;

    private EmiCompatibility() {
    }

    /** Safe during mixin configuration loading; does not resolve any EMI target class. */
    public static void validateVersionOrThrow() {
        String version = currentVersion();
        if (!isSupportedVersion(version)) {
            throw unsupportedVersion(version);
        }
    }

    /** Validates version and non-mixin runtime contracts without defining EMI classes. */
    public static void validateEarlyOrThrow() {
        validateVersionOrThrow();
        EarlyBytecodeValidator.validate();
    }

    /**
     * Performs a pure supported-range check for EMI version metadata.
     *
     * @param version declared EMI version
     * @return whether the version belongs to the supported 1.21.1 range
     */
    public static boolean isSupportedVersion(String version) {
        if (version == null) {
            return false;
        }
        Matcher matcher = SUPPORTED_VERSION.matcher(version);
        if (!matcher.matches()) {
            return false;
        }

        try {
            int patch = Integer.parseInt(matcher.group(1));
            return patch >= MIN_PATCH && patch <= MAX_PATCH;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /** Retained for loader entrypoints; target structure is checked from mixin bytecode in preApply. */
    public static void validateOrThrow() {
        validateEarlyOrThrow();
    }

    /**
     * Validates a registered mixin target without loading it through reflection.
     *
     * @param targetClassName binary target class name
     * @param targetClass target bytecode supplied by Mixin
     * @throws IllegalStateException when an injected field, method, or call site is incompatible
     */
    public static void validateTargetOrThrow(String targetClassName, ClassNode targetClass) {
        validateVersionOrThrow();
        List<String> missing = new ArrayList<>();
        switch (targetClassName) {
            case "dev.emi.emi.bom.BoM" -> {
                requireField(targetClass, "tree", "Ldev/emi/emi/bom/MaterialTree;", true, missing);
                requireField(targetClass, "craftingMode", "Z", true, missing);
                requireMethod(targetClass, "setGoal", "(Ldev/emi/emi/api/recipe/EmiRecipe;)V", true, missing);
            }
            case "dev.emi.emi.bom.MaterialTree" -> {
                requireMethod(targetClass, "<init>", "(Ldev/emi/emi/api/recipe/EmiRecipe;)V", false, missing);
                requireField(targetClass, "goal", "Ldev/emi/emi/bom/MaterialNode;", false, missing);
                requireField(targetClass, "resolutions", "Ljava/util/Map;", false, missing);
                requireField(targetClass, "batches", "J", false, missing);
                requireMethod(targetClass, "addResolution",
                        "(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V", false,
                        missing);
            }
            case "dev.emi.emi.widget.RecipeTreeButtonWidget" -> {
                requireMethod(targetClass, "<init>", "(IILdev/emi/emi/api/recipe/EmiRecipe;)V", false, missing);
                requireMethod(targetClass, "getTextureOffset", "(II)I", false, missing);
                requireMethod(targetClass, "mouseClicked", "(III)Z", false, missing);
                requireMethod(targetClass, "getTooltip", "(II)Ljava/util/List;", false, missing);
            }
            case "dev.emi.emi.widget.RecipeButtonWidget" -> {
                requireProtectedField(targetClass, "recipe", "Ldev/emi/emi/api/recipe/EmiRecipe;", false, missing);
                requireMethod(targetClass, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", false, missing);
                requireFieldAccesses(targetClass, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                        Opcodes.GETSTATIC, "dev/emi/emi/EmiRenderHelper", "BUTTONS",
                        "Lnet/minecraft/resources/ResourceLocation;", 1, missing);
            }
            case "dev.emi.emi.runtime.EmiReloadManager" -> {
                requireMethod(targetClass, "clear", "()V", true, missing);
                requireMethod(targetClass, "reload", "()V", true, missing);
                requireMethod(targetClass, "isLoaded", "()Z", true, missing);
            }
            case "dev.emi.emi.runtime.EmiFavorites" -> requireMethod(targetClass, "updateSynthetic",
                    "(Ldev/emi/emi/api/recipe/EmiPlayerInventory;)V", true, missing);
            case "dev.emi.emi.runtime.EmiSidebars" -> requireMethod(targetClass, "getStacks",
                    "(Ldev/emi/emi/config/SidebarType;)Ljava/util/List;", true, missing);
            case "dev.emi.emi.screen.widget.EmiSearchWidget" ->
                    requireMethod(targetClass, "keyPressed", "(III)Z", false, missing);
            case "dev.emi.emi.screen.widget.config.ConfigEntryWidget" -> requireMethod(targetClass, "render",
                    "(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", false, missing);
            case "dev.emi.emi.screen.widget.config.EmiBindWidget" -> requirePrivateField(targetClass, "bind",
                    "Ldev/emi/emi/input/EmiBind;", false, missing);
            case "dev.emi.emi.screen.ConfigScreen" -> {
                requireField(targetClass, "list", "Ldev/emi/emi/screen/widget/config/ListWidget;", false, missing);
                requireField(targetClass, "activeBind", "Ldev/emi/emi/input/EmiBind;", false, missing);
                requirePrivateField(targetClass, "search",
                        "Ldev/emi/emi/screen/widget/config/ConfigSearch;", false, missing);
                requireProtectedMethod(targetClass, "init", "()V", false, missing);
                requirePrivateMethod(targetClass, "addJumpButtons", "()V", false, missing);
                requireField(targetClass, "originalConfig", "Ljava/lang/String;", false, missing);
                requireField(targetClass, "resetButton", "Lnet/minecraft/client/gui/components/Button;", false,
                        missing);
                requireMethod(targetClass, "jump", "(Ljava/lang/String;)V", false, missing);
                requireMethod(targetClass, "updateChanges", "()V", false, missing);
                requireVariableStores(targetClass, "updateChanges", "()V", 3, 1, missing);
                requireMethod(targetClass, "mouseClicked", "(DDI)Z", false, missing);
                requireMethod(targetClass, "keyPressed", "(III)Z", false, missing);
                requireMethod(targetClass, "keyReleased", "(III)Z", false, missing);
                requireNewInstructions(targetClass, "addJumpButtons", "()V",
                        "dev/emi/emi/screen/widget/config/ConfigJumpButton", 1, missing);
                requireMethodInvocations(targetClass, "addJumpButtons", "()V", Opcodes.INVOKEVIRTUAL,
                        "dev/emi/emi/screen/widget/config/ListWidget", "getLogicalHeight", "()I", 2, missing);
                requireMethodInvocations(targetClass, "init", "()V", Opcodes.INVOKEVIRTUAL,
                        "dev/emi/emi/screen/ConfigScreen", "addJumpButtons", "()V", 1, missing);
                requireMethodInvocations(targetClass, "init", "()V", Opcodes.INVOKESTATIC,
                        "dev/emi/emi/EmiPort", "newButton",
                        "(IIIILnet/minecraft/network/chat/Component;"
                                + "Lnet/minecraft/client/gui/components/Button$OnPress;)"
                                + "Lnet/minecraft/client/gui/components/Button;",
                        3, missing);
            }
            case "dev.emi.emi.screen.RecipeScreen" -> {
                requireMethod(targetClass, "<init>",
                        "(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Ljava/util/Map;)V",
                        false, missing);
                requireMethod(targetClass, "onClose", "()V", false, missing);
                requireMethod(targetClass, "keyPressed", "(III)Z", false, missing);
                requireField(targetClass, "resolve", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
                requirePrivateField(targetClass, "currentPage", "Ljava/util/List;", false, missing);
                requireMethodInvocations(targetClass, "keyPressed", "(III)Z", Opcodes.INVOKESTATIC,
                        "dev/emi/emi/screen/EmiScreenManager", "recipeInteraction",
                        "(Ldev/emi/emi/api/recipe/EmiRecipe;Ljava/util/function/Function;)Z", 1, missing);
            }
            case "dev.emi.emi.api.EmiApi" -> {
                requireMethod(targetClass, "viewRecipeTree", "()V", true, missing);
                requireMethod(targetClass, "getHandledScreen",
                        "()Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;", true, missing);
                requireMethod(targetClass, "getRecipeManager",
                        "()Ldev/emi/emi/api/recipe/EmiRecipeManager;", true, missing);
                requirePrivateMethod(targetClass, "push", "()V", true, missing);
            }
            case "dev.emi.emi.screen.EmiScreenManager" -> {
                requireMethod(targetClass, "keyPressed", "(III)Z", true, missing);
                requireMethod(targetClass, "genericInteraction", "(Ljava/util/function/Function;)Z", true, missing);
                requireMethod(targetClass, "stackInteraction",
                        "(Ldev/emi/emi/api/stack/EmiStackInteraction;Ljava/util/function/Function;)Z", true,
                        missing);
                requireMethod(targetClass, "mouseClicked", "(DDI)Z", true, missing);
                requireMethod(targetClass, "mouseReleased", "(DDI)Z", true, missing);
                requireMethod(targetClass, "mouseDragged", "(DDIDD)Z", true, missing);
                requireMethod(targetClass, "repopulatePanels", "(Ldev/emi/emi/config/SidebarType;)V", true, missing);
                requireMethod(targetClass, "getHoveredStack",
                        "(IIZ)Ldev/emi/emi/api/stack/EmiStackInteraction;", true, missing);
                requireField(targetClass, "pressedStack", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
                requireField(targetClass, "draggedStack", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
                requireField(targetClass, "search", "Ldev/emi/emi/screen/widget/EmiSearchWidget;", true, missing);
                requireField(targetClass, "tree", "Ldev/emi/emi/screen/widget/SizedButtonWidget;", true, missing);
                requireField(targetClass, "lastPlayerInventory",
                        "Ldev/emi/emi/api/recipe/EmiPlayerInventory;", true, missing);
                requireField(targetClass, "lastMouseX", "I", true, missing);
                requireField(targetClass, "lastMouseY", "I", true, missing);
                requireMethodInvocations(targetClass, "genericInteraction", "(Ljava/util/function/Function;)Z",
                        Opcodes.INVOKESTATIC, "dev/emi/emi/api/EmiApi", "viewRecipeTree", "()V", 1, missing);
            }
            case "dev.emi.emi.runtime.EmiPersistentData" ->
                    requireMethod(targetClass, "load", "()V", true, missing);
            default -> missing.add("registered target " + targetClassName.replace('.', '/'));
        }

        if (!missing.isEmpty()) {
            throw incompatibleContracts(currentVersion(), "target " + targetClassName, missing);
        }
    }

    /**
     * Tests whether a material node represents a catalyst across supported EMI versions.
     *
     * @param node material node, possibly {@code null}
     * @return whether the node represents a catalyst
     */
    public static boolean isCatalyst(MaterialNode node) {
        if (node == null) {
            return false;
        }
        CatalystAccess access = CatalystHolder.ACCESS;
        try {
            if (access.field() != null) {
                return access.field().getBoolean(node);
            }
            if (access.legacyMethod() != null) {
                return (boolean) access.legacyMethod().invoke(null, node.ingredient);
            }
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw catalystFailure(exception);
        }
        throw catalystFailure(null);
    }

    private static String currentVersion() {
        return Services.getModVersion("emi").orElse("<not loaded>");
    }

    private static IllegalStateException unsupportedVersion(String version) {
        return new IllegalStateException("Unsupported EMI version '" + version
                + "'. RecipeForest supports EMI " + SUPPORTED_RANGE + '.');
    }

    private static IllegalStateException incompatibleContracts(String version, String phase, List<String> missing) {
        return new IllegalStateException("Incompatible EMI " + version + ' ' + phase
                + ". RecipeForest supports EMI " + SUPPORTED_RANGE
                + ". Missing or incompatible descriptors: " + String.join(", ", missing));
    }

    private static boolean hasPublicMethod(ClassNode owner, String name, String descriptor, boolean requireStatic) {
        if (owner == null) {
            return false;
        }
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)
                    && (method.access & Opcodes.ACC_PUBLIC) != 0
                    && ((method.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPublicField(ClassNode owner, String name, String descriptor, boolean requireStatic) {
        if (owner == null) {
            return false;
        }
        for (FieldNode field : owner.fields) {
            if (field.name.equals(name) && field.desc.equals(descriptor)
                    && (field.access & Opcodes.ACC_PUBLIC) != 0
                    && ((field.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                return true;
            }
        }
        return false;
    }

    private static void requireMethod(ClassNode owner, String name, String descriptor, boolean requireStatic,
            List<String> missing) {
        if (hasPublicMethod(owner, name, descriptor, requireStatic)) {
            return;
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + descriptor);
    }

    private static void requireProtectedMethod(ClassNode owner, String name, String descriptor,
            boolean requireStatic, List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                int visibility = method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
                if (method.name.equals(name) && method.desc.equals(descriptor)
                        && visibility == Opcodes.ACC_PROTECTED
                        && ((method.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                    return;
                }
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + descriptor + " [protected]");
    }

    private static void requirePrivateMethod(ClassNode owner, String name, String descriptor,
            boolean requireStatic, List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                int visibility = method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
                if (method.name.equals(name) && method.desc.equals(descriptor)
                        && visibility == Opcodes.ACC_PRIVATE
                        && ((method.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                    return;
                }
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + descriptor + " [private]");
    }

    private static void requireNewInstructions(ClassNode owner, String methodName, String methodDescriptor,
            String type, int expectedCount, List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                if (!method.name.equals(methodName) || !method.desc.equals(methodDescriptor)) {
                    continue;
                }
                int count = 0;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == Opcodes.NEW && instruction instanceof TypeInsnNode typeInsn
                            && typeInsn.desc.equals(type)) {
                        count++;
                    }
                }
                if (count == expectedCount) {
                    return;
                }
                missing.add(owner.name + '.' + methodName + methodDescriptor + " exactly " + expectedCount
                        + " NEW " + type + " instructions (found " + count + ')');
                return;
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + methodName + methodDescriptor
                + " exactly " + expectedCount + " NEW " + type + " instructions");
    }

    private static void requireMethodInvocations(ClassNode owner, String methodName, String methodDescriptor,
            int opcode, String targetOwner, String targetName, String targetDescriptor, int expectedCount,
            List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                if (!method.name.equals(methodName) || !method.desc.equals(methodDescriptor)) {
                    continue;
                }
                int count = 0;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == opcode && instruction instanceof MethodInsnNode invocation
                            && invocation.owner.equals(targetOwner) && invocation.name.equals(targetName)
                            && invocation.desc.equals(targetDescriptor)) {
                        count++;
                    }
                }
                if (count == expectedCount) {
                    return;
                }
                missing.add(owner.name + '.' + methodName + methodDescriptor + " exactly " + expectedCount + ' '
                        + targetOwner + '.' + targetName + targetDescriptor + " invocations (found " + count + ')');
                return;
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + methodName + methodDescriptor
                + " exactly " + expectedCount + ' ' + targetOwner + '.' + targetName + targetDescriptor
                + " invocations");
    }

    private static void requireVariableStores(ClassNode owner, String methodName, String methodDescriptor,
            int variable, int expectedCount, List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                if (!method.name.equals(methodName) || !method.desc.equals(methodDescriptor)) {
                    continue;
                }
                int count = 0;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == Opcodes.ISTORE
                            && instruction instanceof VarInsnNode variableInstruction
                            && variableInstruction.var == variable) {
                        count++;
                    }
                }
                if (count == expectedCount) {
                    return;
                }
                missing.add(owner.name + '.' + methodName + methodDescriptor + " exactly " + expectedCount + ' '
                        + "ISTORE local " + variable + " instructions (found " + count + ')');
                return;
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + methodName + methodDescriptor
                + " exactly " + expectedCount + " ISTORE local " + variable + " instructions");
    }

    private static void requireFieldAccesses(ClassNode owner, String methodName, String methodDescriptor,
            int opcode, String targetOwner, String targetName, String targetDescriptor, int expectedCount,
            List<String> missing) {
        if (owner != null) {
            for (MethodNode method : owner.methods) {
                if (!method.name.equals(methodName) || !method.desc.equals(methodDescriptor)) {
                    continue;
                }
                int count = 0;
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == opcode && instruction instanceof FieldInsnNode access
                            && access.owner.equals(targetOwner) && access.name.equals(targetName)
                            && access.desc.equals(targetDescriptor)) {
                        count++;
                    }
                }
                if (count == expectedCount) {
                    return;
                }
                missing.add(owner.name + '.' + methodName + methodDescriptor + " exactly " + expectedCount + ' '
                        + targetOwner + '.' + targetName + ':' + targetDescriptor + " field accesses (found "
                        + count + ')');
                return;
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + methodName + methodDescriptor
                + " exactly " + expectedCount + ' ' + targetOwner + '.' + targetName + ':' + targetDescriptor
                + " field accesses");
    }

    private static void requireField(ClassNode owner, String name, String descriptor, boolean requireStatic,
            List<String> missing) {
        if (hasPublicField(owner, name, descriptor, requireStatic)) {
            return;
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + ':' + descriptor);
    }

    private static void requirePrivateField(ClassNode owner, String name, String descriptor, boolean requireStatic,
            List<String> missing) {
        if (owner != null) {
            for (FieldNode field : owner.fields) {
                int visibility = field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
                if (field.name.equals(name) && field.desc.equals(descriptor)
                        && visibility == Opcodes.ACC_PRIVATE
                        && ((field.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                    return;
                }
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + ':' + descriptor
                + " [private]");
    }

    private static void requireProtectedField(ClassNode owner, String name, String descriptor, boolean requireStatic,
            List<String> missing) {
        if (owner != null) {
            for (FieldNode field : owner.fields) {
                int visibility = field.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
                if (field.name.equals(name) && field.desc.equals(descriptor)
                        && visibility == Opcodes.ACC_PROTECTED
                        && ((field.access & Opcodes.ACC_STATIC) != 0) == requireStatic) {
                    return;
                }
            }
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + ':' + descriptor
                + " [protected]");
    }

    private static void requireExtensibleClass(ClassNode owner, List<String> missing) {
        if (owner != null && (owner.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_FINAL)) == 0) {
            return;
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + " [non-final class]");
    }

    private static void requireSuperclass(ClassNode owner, String superclass, List<String> missing) {
        if (owner != null && superclass.equals(owner.superName)) {
            return;
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + " extends " + superclass);
    }

    private static CatalystAccess resolveCatalystAccess() {
        try {
            Field field = MaterialNode.class.getField("catalyst");
            int modifiers = field.getModifiers();
            if (field.getType() == boolean.class && Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
                return new CatalystAccess(field, null);
            }
        } catch (NoSuchFieldException | LinkageError exception) {
            // EMI 1.1.13 through 1.1.21 calculate catalysts through TreeCost.
        }

        try {
            Method method = TreeCost.class.getMethod("isCatalyst", EmiIngredient.class);
            int modifiers = method.getModifiers();
            if (method.getReturnType() == boolean.class && Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)) {
                return new CatalystAccess(null, method);
            }
        } catch (NoSuchMethodException | LinkageError exception) {
            // Report the combined missing contract on first use.
        }
        return new CatalystAccess(null, null);
    }

    private static IllegalStateException catalystFailure(Throwable cause) {
        return new IllegalStateException("Incompatible EMI catalyst API. RecipeForest supports EMI " + SUPPORTED_RANGE
                + "; expected MaterialNode.catalyst:Z or TreeCost.isCatalyst(EmiIngredient)Z.", cause);
    }

    private static final class EarlyBytecodeValidator {
        private static void validate() {
            List<String> missing = new ArrayList<>();
            ClassNode materialNode = readClass("dev/emi/emi/bom/MaterialNode", missing);
            ClassNode treeCost = readClass("dev/emi/emi/bom/TreeCost", missing);
            ClassNode synthetic = readClass("dev/emi/emi/runtime/EmiFavorite$Synthetic", missing);
            ClassNode bomScreen = readClass("dev/emi/emi/screen/BoMScreen", missing);
            ClassNode sizedButton = readClass("dev/emi/emi/screen/widget/SizedButtonWidget", missing);
            ClassNode recipeButton = readClass("dev/emi/emi/widget/RecipeButtonWidget", missing);
            ClassNode materialTree = readClass("dev/emi/emi/bom/MaterialTree", missing);
            ClassNode recipeScreen = readClass("dev/emi/emi/screen/RecipeScreen", missing);
            ClassNode reloadManager = readClass("dev/emi/emi/runtime/EmiReloadManager", missing);
            ClassNode configScreen = readClass("dev/emi/emi/screen/ConfigScreen", missing);
            ClassNode configEnumScreen = readClass("dev/emi/emi/screen/ConfigEnumScreen", missing);
            ClassNode configEnumEntry = readClass("dev/emi/emi/screen/ConfigEnumScreen$Entry", missing);
            ClassNode configList = readClass("dev/emi/emi/screen/widget/config/ListWidget", missing);
            ClassNode configGroup = readClass("dev/emi/emi/screen/widget/config/GroupNameWidget", missing);
            ClassNode configEntry = readClass("dev/emi/emi/screen/widget/config/ConfigEntryWidget", missing);
            ClassNode configSearch = readClass("dev/emi/emi/screen/widget/config/ConfigSearch", missing);
            ClassNode configJumpButton = readClass("dev/emi/emi/screen/widget/config/ConfigJumpButton", missing);
            ClassNode bindWidget = readClass("dev/emi/emi/screen/widget/config/EmiBindWidget", missing);
            ClassNode intEdit = readClass("dev/emi/emi/screen/widget/config/IntEdit", missing);
            ClassNode intGroup = readClass("dev/emi/emi/config/IntGroup", missing);
            ClassNode intGroupWidget = readClass("dev/emi/emi/screen/widget/config/IntGroupWidget", missing);
            ClassNode screenManager = readClass("dev/emi/emi/screen/EmiScreenManager", missing);
            ClassNode renderHelper = readClass("dev/emi/emi/EmiRenderHelper", missing);
            ClassNode emiConfig = readClass("dev/emi/emi/config/EmiConfig", missing);
            ClassNode configValue = readClass("dev/emi/emi/config/EmiConfig$ConfigValue", missing);
            ClassNode emiBind = readClass("dev/emi/emi/input/EmiBind", missing);
            ClassNode modifiedKey = readClass("dev/emi/emi/input/EmiBind$ModifiedKey", missing);
            ClassNode emiInput = readClass("dev/emi/emi/input/EmiInput", missing);
            ClassNode stackInteraction = readClass("dev/emi/emi/api/stack/EmiStackInteraction", missing);
            ClassNode emiApi = readClass("dev/emi/emi/api/EmiApi", missing);
            ClassNode recipeManager = readClass("dev/emi/emi/api/recipe/EmiRecipeManager", missing);
            ClassNode emiUtil = readClass("dev/emi/emi/EmiUtil", missing);
            ClassNode ingredientSerializer = readClass(
                    "dev/emi/emi/api/stack/serializer/EmiIngredientSerializer", missing);
            ClassNode resolutionRecipe = readClass("dev/emi/emi/api/recipe/EmiResolutionRecipe", missing);
            ClassNode emiRecipe = readClass("dev/emi/emi/api/recipe/EmiRecipe", missing);
            ClassNode emiIngredient = readClass("dev/emi/emi/api/stack/EmiIngredient", missing);
            ClassNode emiPort = readClass("dev/emi/emi/EmiPort", missing);

            boolean modernCatalyst = hasPublicField(materialNode, "catalyst", "Z", false);
            boolean legacyCatalyst = hasPublicMethod(treeCost, "isCatalyst",
                    "(Ldev/emi/emi/api/stack/EmiIngredient;)Z", true);
            if (!modernCatalyst && !legacyCatalyst) {
                missing.add("one of dev/emi/emi/bom/MaterialNode.catalyst:Z or "
                        + "dev/emi/emi/bom/TreeCost.isCatalyst(Ldev/emi/emi/api/stack/EmiIngredient;)Z");
            }

            requireMethod(synthetic, "<init>", "(Ldev/emi/emi/api/stack/EmiIngredient;JJ)V", false, missing);
            boolean legacyRecipeSynthetic = hasPublicMethod(synthetic, "<init>",
                    "(Ldev/emi/emi/api/recipe/EmiRecipe;JJI)V", false);
            boolean modernRecipeSynthetic = hasPublicMethod(synthetic, "<init>",
                    "(Ldev/emi/emi/api/recipe/EmiRecipe;JJJI)V", false);
            if (!legacyRecipeSynthetic && !modernRecipeSynthetic) {
                missing.add("one of dev/emi/emi/runtime/EmiFavorite$Synthetic.<init>"
                        + "(Ldev/emi/emi/api/recipe/EmiRecipe;JJI)V or "
                        + "dev/emi/emi/runtime/EmiFavorite$Synthetic.<init>"
                        + "(Ldev/emi/emi/api/recipe/EmiRecipe;JJJI)V");
            }

            requireMethod(bomScreen, "<init>",
                    "(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;)V", false, missing);
            requireField(bomScreen, "old",
                    "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;", false, missing);
            requireMethod(sizedButton, "<init>",
                    "(IIIIIILjava/util/function/BooleanSupplier;"
                            + "Lnet/minecraft/client/gui/components/Button$OnPress;Ljava/util/List;)V",
                    false, missing);
            requireProtectedField(sizedButton, "texture", "Lnet/minecraft/resources/ResourceLocation;", false,
                    missing);
            requireProtectedField(recipeButton, "recipe", "Ldev/emi/emi/api/recipe/EmiRecipe;", false, missing);
            requireMethod(recipeButton, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", false, missing);
            requireFieldAccesses(recipeButton, "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    Opcodes.GETSTATIC, "dev/emi/emi/EmiRenderHelper", "BUTTONS",
                    "Lnet/minecraft/resources/ResourceLocation;", 1, missing);
            requireMethod(materialTree, "<init>", "(Ldev/emi/emi/api/recipe/EmiRecipe;)V", false, missing);
            requireField(materialTree, "goal", "Ldev/emi/emi/bom/MaterialNode;", false, missing);
            requireField(materialTree, "resolutions", "Ljava/util/Map;", false, missing);
            requireField(materialTree, "batches", "J", false, missing);
            requireMethod(materialTree, "addResolution",
                    "(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V", false,
                    missing);
            requireField(materialNode, "recipe", "Ldev/emi/emi/api/recipe/EmiRecipe;", false, missing);
            requireField(materialNode, "children", "Ljava/util/List;", false, missing);
            requireField(materialNode, "state", "Ldev/emi/emi/bom/FoldState;", false, missing);
            requireMethod(reloadManager, "isLoaded", "()Z", true, missing);
            requireMethod(recipeScreen, "<init>",
                    "(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Ljava/util/Map;)V",
                    false, missing);
            requireMethod(recipeScreen, "onClose", "()V", false, missing);
            requireField(recipeScreen, "resolve", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
            requireField(configScreen, "list", "Ldev/emi/emi/screen/widget/config/ListWidget;", false, missing);
            requireField(configScreen, "activeBind", "Ldev/emi/emi/input/EmiBind;", false, missing);
            requireField(configScreen, "originalConfig", "Ljava/lang/String;", false, missing);
            requireField(configScreen, "resetButton", "Lnet/minecraft/client/gui/components/Button;", false,
                    missing);
            requirePrivateField(configScreen, "search",
                    "Ldev/emi/emi/screen/widget/config/ConfigSearch;", false, missing);
            requireProtectedMethod(configScreen, "init", "()V", false, missing);
            requirePrivateMethod(configScreen, "addJumpButtons", "()V", false, missing);
            requireMethod(configScreen, "jump", "(Ljava/lang/String;)V", false, missing);
            requireMethod(configScreen, "updateChanges", "()V", false, missing);
            requireVariableStores(configScreen, "updateChanges", "()V", 3, 1, missing);
            requireMethod(configScreen, "mouseClicked", "(DDI)Z", false, missing);
            requireMethod(configScreen, "keyPressed", "(III)Z", false, missing);
            requireMethod(configScreen, "keyReleased", "(III)Z", false, missing);
            requireNewInstructions(configScreen, "addJumpButtons", "()V",
                    "dev/emi/emi/screen/widget/config/ConfigJumpButton", 1, missing);
            requireMethodInvocations(configScreen, "addJumpButtons", "()V", Opcodes.INVOKEVIRTUAL,
                    "dev/emi/emi/screen/widget/config/ListWidget", "getLogicalHeight", "()I", 2, missing);
            requireMethodInvocations(configScreen, "init", "()V", Opcodes.INVOKEVIRTUAL,
                    "dev/emi/emi/screen/ConfigScreen", "addJumpButtons", "()V", 1, missing);
            requireMethodInvocations(configScreen, "init", "()V", Opcodes.INVOKESTATIC,
                    "dev/emi/emi/EmiPort", "newButton",
                    "(IIIILnet/minecraft/network/chat/Component;"
                            + "Lnet/minecraft/client/gui/components/Button$OnPress;)"
                            + "Lnet/minecraft/client/gui/components/Button;",
                    3, missing);
            requireMethod(configEnumScreen, "<init>",
                    "(Ldev/emi/emi/screen/ConfigScreen;Ljava/util/List;Ljava/util/function/Consumer;)V",
                    false, missing);
            requireMethod(configEnumEntry, "<init>",
                    "(Ljava/lang/Object;Lnet/minecraft/network/chat/Component;Ljava/util/List;)V",
                    false, missing);
            requireMethod(configList, "children", "()Ljava/util/List;", false, missing);
            requireMethod(configList, "addEntry",
                    "(Ldev/emi/emi/screen/widget/config/ListWidget$Entry;)I", false, missing);
            requireMethod(configGroup, "<init>",
                    "(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V", false, missing);
            requireField(configGroup, "id", "Ljava/lang/String;", false, missing);
            requireField(configGroup, "children", "Ljava/util/List;", false, missing);
            requireField(configGroup, "collapsed", "Z", false, missing);
            requireMethod(configEntry, "<init>",
                    "(Lnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/util/function/Supplier;I)V",
                    false, missing);
            requireMethod(configEntry, "setChildren", "(Ljava/util/List;)V", false, missing);
            requireMethod(configEntry, "update", "(IIII)V", false, missing);
            requireMethod(configEntry, "render",
                    "(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", false, missing);
            requireMethod(configEntry, "getTooltip", "(II)Ljava/util/List;", false, missing);
            requireField(configEntry, "parentGroups", "Ljava/util/List;", false, missing);
            requireMethod(configSearch, "getSearch", "()Ljava/lang/String;", false, missing);
            requireSuperclass(configJumpButton, "dev/emi/emi/screen/widget/SizedButtonWidget", missing);
            requireExtensibleClass(configJumpButton, missing);
            requireMethod(configJumpButton, "<init>",
                    "(IIIILnet/minecraft/client/gui/components/Button$OnPress;Ljava/util/List;)V", false, missing);
            requireExtensibleClass(bindWidget, missing);
            requireMethod(bindWidget, "<init>",
                    "(Ldev/emi/emi/screen/ConfigScreen;Ljava/util/List;Ljava/util/function/Supplier;"
                            + "Ldev/emi/emi/input/EmiBind;)V",
                    false, missing);
            requireMethod(bindWidget, "update", "(IIII)V", false, missing);
            requirePrivateField(bindWidget, "bind", "Ldev/emi/emi/input/EmiBind;", false, missing);
            requireMethod(intEdit, "<init>",
                    "(ILjava/util/function/IntSupplier;Ljava/util/function/IntConsumer;)V", false, missing);
            requireField(intEdit, "text", "Lnet/minecraft/client/gui/components/EditBox;", false, missing);
            requireField(intEdit, "up", "Lnet/minecraft/client/gui/components/Button;", false, missing);
            requireField(intEdit, "down", "Lnet/minecraft/client/gui/components/Button;", false, missing);
            requireMethod(intEdit, "setPosition", "(II)V", false, missing);
            requireMethod(intGroup, "<init>",
                    "(Ljava/lang/String;Ljava/util/List;Lit/unimi/dsi/fastutil/ints/IntList;)V", false, missing);
            requireField(intGroup, "values", "Lit/unimi/dsi/fastutil/ints/IntList;", false, missing);
            requireMethod(intGroupWidget, "<init>",
                    "(Lnet/minecraft/network/chat/Component;Ljava/util/List;Ljava/util/function/Supplier;"
                            + "Ldev/emi/emi/screen/ConfigScreen$Mutator;)V",
                    false, missing);
            requireMethod(screenManager, "keyPressed", "(III)Z", true, missing);
            requireMethod(screenManager, "genericInteraction", "(Ljava/util/function/Function;)Z", true, missing);
            requireMethod(screenManager, "stackInteraction",
                    "(Ldev/emi/emi/api/stack/EmiStackInteraction;Ljava/util/function/Function;)Z", true, missing);
            requireMethod(screenManager, "recipeInteraction",
                    "(Ldev/emi/emi/api/recipe/EmiRecipe;Ljava/util/function/Function;)Z", true, missing);
            requireMethod(screenManager, "mouseClicked", "(DDI)Z", true, missing);
            requireMethod(screenManager, "getHoveredStack",
                    "(IIZ)Ldev/emi/emi/api/stack/EmiStackInteraction;", true, missing);
            requireField(screenManager, "lastPlayerInventory",
                    "Ldev/emi/emi/api/recipe/EmiPlayerInventory;", true, missing);
            requireField(screenManager, "lastMouseX", "I", true, missing);
            requireField(screenManager, "lastMouseY", "I", true, missing);
            requireMethodInvocations(screenManager, "genericInteraction", "(Ljava/util/function/Function;)Z",
                    Opcodes.INVOKESTATIC, "dev/emi/emi/api/EmiApi", "viewRecipeTree", "()V", 1, missing);
            requireMethod(recipeScreen, "keyPressed", "(III)Z", false, missing);
            requirePrivateField(recipeScreen, "currentPage", "Ljava/util/List;", false, missing);
            requireMethodInvocations(recipeScreen, "keyPressed", "(III)Z", Opcodes.INVOKESTATIC,
                    "dev/emi/emi/screen/EmiScreenManager", "recipeInteraction",
                    "(Ldev/emi/emi/api/recipe/EmiRecipe;Ljava/util/function/Function;)Z", 1, missing);
            requireMethod(renderHelper, "drawTooltip",
                    "(Lnet/minecraft/client/gui/screens/Screen;Ldev/emi/emi/runtime/EmiDrawContext;Ljava/util/List;II)V",
                    true, missing);
            requireMethod(configValue, "value", "()Ljava/lang/String;", false, missing);
            requireExtensibleClass(emiBind, missing);
            requireMethod(emiBind, "<init>",
                    "(Ljava/lang/String;[Ldev/emi/emi/input/EmiBind$ModifiedKey;)V", false, missing);
            requireField(emiBind, "translationKey", "Ljava/lang/String;", false, missing);
            requireField(emiBind, "boundKeys", "Ljava/util/List;", false, missing);
            requireMethod(emiBind, "setBind", "(ILdev/emi/emi/input/EmiBind$ModifiedKey;)V", false, missing);
            requireMethod(emiBind, "setBinds", "([Ldev/emi/emi/input/EmiBind$ModifiedKey;)V", false, missing);
            requireMethod(emiBind, "setToDefault", "()V", false, missing);
            requireMethod(emiBind, "matchesKey", "(II)Z", false, missing);
            requireMethod(emiBind, "matchesMouse", "(I)Z", false, missing);
            requireMethod(modifiedKey, "<init>",
                    "(Lcom/mojang/blaze3d/platform/InputConstants$Key;I)V", false, missing);
            requireMethod(modifiedKey, "key",
                    "()Lcom/mojang/blaze3d/platform/InputConstants$Key;", false, missing);
            requireMethod(modifiedKey, "modifiers", "()I", false, missing);
            requireMethod(modifiedKey, "modifiersToMatch", "()I", false, missing);
            requireMethod(modifiedKey, "isUnbound", "()Z", false, missing);
            requireMethod(emiInput, "getCurrentModifiers", "()I", true, missing);
            requireField(emiInput, "SHIFT_MASK", "I", true, missing);
            requireMethod(stackInteraction, "isEmpty", "()Z", false, missing);
            requireMethod(stackInteraction, "getRecipeContext",
                    "()Ldev/emi/emi/api/recipe/EmiRecipe;", false, missing);
            requireMethod(stackInteraction, "getStack",
                    "()Ldev/emi/emi/api/stack/EmiIngredient;", false, missing);
            requirePrivateMethod(emiApi, "push", "()V", true, missing);
            requireMethod(emiApi, "getRecipeManager",
                    "()Ldev/emi/emi/api/recipe/EmiRecipeManager;", true, missing);
            requireMethod(recipeManager, "getRecipesByOutput",
                    "(Ldev/emi/emi/api/stack/EmiStack;)Ljava/util/List;", false, missing);
            requireMethod(recipeManager, "getRecipe",
                    "(Lnet/minecraft/resources/ResourceLocation;)Ldev/emi/emi/api/recipe/EmiRecipe;",
                    false, missing);
            requireMethod(emiUtil, "getPreferredRecipe",
                    "(Ljava/util/List;Ldev/emi/emi/api/recipe/EmiPlayerInventory;Z)"
                            + "Ldev/emi/emi/api/recipe/EmiRecipe;",
                    true, missing);
            requireMethod(ingredientSerializer, "getSerialized",
                    "(Ldev/emi/emi/api/stack/EmiIngredient;)Lcom/google/gson/JsonElement;", true, missing);
            requireMethod(ingredientSerializer, "getDeserialized",
                    "(Lcom/google/gson/JsonElement;)Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
            requireMethod(resolutionRecipe, "<init>",
                    "(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/stack/EmiStack;)V",
                    false, missing);
            requireField(resolutionRecipe, "stack", "Ldev/emi/emi/api/stack/EmiStack;", false, missing);
            requireMethod(emiRecipe, "getId", "()Lnet/minecraft/resources/ResourceLocation;", false, missing);
            requireMethod(emiRecipe, "supportsRecipeTree", "()Z", false, missing);
            requireMethod(emiIngredient, "isEmpty", "()Z", false, missing);
            requireMethod(emiIngredient, "getEmiStacks", "()Ljava/util/List;", false, missing);
            requireMethod(emiPort, "id",
                    "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", true, missing);

            if (!missing.isEmpty()) {
                throw incompatibleContracts(currentVersion(), "runtime preflight", missing);
            }
        }

        private static ClassNode readClass(String internalName, List<String> missing) {
            String resource = internalName + ".class";
            try (InputStream input = EmiCompatibility.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    missing.add("class " + internalName);
                    return null;
                }
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            } catch (IOException | RuntimeException exception) {
                missing.add("class " + internalName + " (unreadable: "
                        + exception.getClass().getSimpleName() + ')');
                return null;
            }
        }
    }

    private static final class CatalystHolder {
        private static final CatalystAccess ACCESS = resolveCatalystAccess();
    }

    private record CatalystAccess(Field field, Method legacyMethod) {
    }
}
