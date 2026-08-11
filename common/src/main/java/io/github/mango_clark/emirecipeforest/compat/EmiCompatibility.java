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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/** Verifies the EMI implementation surface used by RecipeForest mixins. */
public final class EmiCompatibility {
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

    /** Pure supported-range check for EMI version metadata. */
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

    /** Validates a registered mixin target without loading it through reflection. */
    public static void validateTargetOrThrow(String targetClassName, ClassNode targetClass) {
        validateVersionOrThrow();
        List<String> missing = new ArrayList<>();
        switch (targetClassName) {
            case "dev.emi.emi.bom.BoM" -> {
                requireField(targetClass, "tree", "Ldev/emi/emi/bom/MaterialTree;", true, missing);
                requireField(targetClass, "craftingMode", "Z", true, missing);
                requireMethod(targetClass, "setGoal", "(Ldev/emi/emi/api/recipe/EmiRecipe;)V", true, missing);
            }
            case "dev.emi.emi.widget.RecipeTreeButtonWidget" -> {
                requireMethod(targetClass, "<init>", "(IILdev/emi/emi/api/recipe/EmiRecipe;)V", false, missing);
                requireMethod(targetClass, "mouseClicked", "(III)Z", false, missing);
                requireMethod(targetClass, "getTooltip", "(II)Ljava/util/List;", false, missing);
            }
            case "dev.emi.emi.runtime.EmiReloadManager" -> {
                requireMethod(targetClass, "clear", "()V", true, missing);
                requireMethod(targetClass, "reload", "()V", true, missing);
            }
            case "dev.emi.emi.runtime.EmiFavorites" -> requireMethod(targetClass, "updateSynthetic",
                    "(Ldev/emi/emi/api/recipe/EmiPlayerInventory;)V", true, missing);
            case "dev.emi.emi.runtime.EmiSidebars" -> requireMethod(targetClass, "getStacks",
                    "(Ldev/emi/emi/config/SidebarType;)Ljava/util/List;", true, missing);
            case "dev.emi.emi.screen.widget.EmiSearchWidget" ->
                    requireMethod(targetClass, "keyPressed", "(III)Z", false, missing);
            case "dev.emi.emi.screen.EmiScreenManager" -> {
                requireMethod(targetClass, "mouseReleased", "(DDI)Z", true, missing);
                requireMethod(targetClass, "mouseDragged", "(DDIDD)Z", true, missing);
                requireMethod(targetClass, "repopulatePanels", "(Ldev/emi/emi/config/SidebarType;)V", true, missing);
                requireField(targetClass, "pressedStack", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
                requireField(targetClass, "draggedStack", "Ldev/emi/emi/api/stack/EmiIngredient;", true, missing);
                requireField(targetClass, "search", "Ldev/emi/emi/screen/widget/EmiSearchWidget;", true, missing);
            }
            case "dev.emi.emi.runtime.EmiPersistentData" ->
                    requireMethod(targetClass, "load", "()V", true, missing);
            default -> missing.add("registered target " + targetClassName.replace('.', '/'));
        }

        if (!missing.isEmpty()) {
            throw incompatibleContracts(currentVersion(), "target " + targetClassName, missing);
        }
    }

    /** Tests whether a material node represents a catalyst across supported EMI versions. */
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

    private static void requireField(ClassNode owner, String name, String descriptor, boolean requireStatic,
            List<String> missing) {
        if (hasPublicField(owner, name, descriptor, requireStatic)) {
            return;
        }
        missing.add((owner == null ? "<missing-class>" : owner.name) + '.' + name + ':' + descriptor);
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
                new ClassReader(input).accept(node,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
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
