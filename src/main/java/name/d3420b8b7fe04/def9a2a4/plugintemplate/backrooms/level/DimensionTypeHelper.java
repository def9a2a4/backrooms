package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.logging.Logger;

/**
 * Uses NMS reflection to swap the DimensionType on a world's ServerLevel.
 * This allows per-world custom dimension types without overriding vanilla types.
 *
 * Falls back gracefully — if reflection fails, the datapack nether override
 * serves as a fallback for dark levels.
 */
public class DimensionTypeHelper {

    private final Logger logger;

    // Cached reflection targets (resolved once)
    private boolean resolved = false;
    private boolean available = false;
    private Method getHandleMethod;
    private Field dimensionTypeField;
    private Method holderDirectMethod;
    private Constructor<?> dimTypeConstructor;
    private RecordComponent[] dimTypeComponents;
    private Class<?> dimTypeClass;

    public DimensionTypeHelper(Logger logger) {
        this.logger = logger;
    }

    /**
     * Apply the dark dimension type to a world.
     * Properties: ambient_light=0, skybox=END, no skylight, has ceiling, no dragon fight.
     */
    public boolean applyDarkDimension(World world) {
        return applyDimension(world, "dark", dimType -> {
            Object[] values = copyComponents(dimType);
            if (values == null) return null;

            for (int i = 0; i < dimTypeComponents.length; i++) {
                String name = dimTypeComponents[i].getName();
                switch (name) {
                    case "ambientLight" -> values[i] = 0.0f;
                    case "hasSkyLight" -> values[i] = false;
                    case "hasCeiling" -> values[i] = true;
                    case "hasFixedTime" -> values[i] = false;
                    case "coordinateScale" -> values[i] = 1.0;
                    case "skybox" -> values[i] = resolveEnum(dimTypeComponents[i].getType(), "END");
                    case "cardinalLightType" -> values[i] = resolveEnum(dimTypeComponents[i].getType(), "DEFAULT");
                    case "monsterSettings" -> values[i] = createMonsterSettings(0, 0);
                }
            }
            return dimTypeConstructor.newInstance(values);
        });
    }

    /**
     * Apply the light dimension type to a world.
     * Properties: ambient_light=1.0, skybox=OVERWORLD, skylight, no ceiling, fixed noon.
     */
    public boolean applyLightDimension(World world) {
        return applyDimension(world, "light", dimType -> {
            Object[] values = copyComponents(dimType);
            if (values == null) return null;

            for (int i = 0; i < dimTypeComponents.length; i++) {
                String name = dimTypeComponents[i].getName();
                switch (name) {
                    case "ambientLight" -> values[i] = 1.0f;
                    case "hasSkyLight" -> values[i] = true;
                    case "hasCeiling" -> values[i] = false;
                    case "hasFixedTime" -> values[i] = true;
                    case "coordinateScale" -> values[i] = 1.0;
                    case "skybox" -> values[i] = resolveEnum(dimTypeComponents[i].getType(), "OVERWORLD");
                    case "cardinalLightType" -> values[i] = resolveEnum(dimTypeComponents[i].getType(), "DEFAULT");
                    case "monsterSettings" -> values[i] = createMonsterSettings(0, 0);
                }
            }
            return dimTypeConstructor.newInstance(values);
        });
    }

    private boolean applyDimension(World world, String label, DimTypeFactory factory) {
        if (!ensureResolved()) {
            return false;
        }
        try {
            Object craftWorld = world.getClass().getMethod("getHandle").invoke(world);
            Object currentDimType = getCurrentDimType(craftWorld);
            Object newDimType = factory.create(currentDimType);
            if (newDimType == null) {
                logger.warning("Failed to construct " + label + " DimensionType");
                return false;
            }
            Object holder = holderDirectMethod.invoke(null, newDimType);
            dimensionTypeField.set(craftWorld, holder);
            logger.info("Applied " + label + " dimension type to world: " + world.getName());
            return true;
        } catch (Exception e) {
            logger.warning("NMS dimension type swap failed for " + world.getName()
                    + " (" + label + "): " + e.getMessage());
            return false;
        }
    }

    private Object getCurrentDimType(Object serverLevel) throws Exception {
        // Find dimensionType() accessor method on the Level hierarchy
        Method accessor = findMethod(serverLevel.getClass(), "dimensionType");
        if (accessor == null) {
            throw new RuntimeException("Cannot find dimensionType() method on ServerLevel");
        }
        return accessor.invoke(serverLevel);
    }

    private Object[] copyComponents(Object dimType) {
        try {
            Object[] values = new Object[dimTypeComponents.length];
            for (int i = 0; i < dimTypeComponents.length; i++) {
                values[i] = dimTypeComponents[i].getAccessor().invoke(dimType);
            }
            return values;
        } catch (Exception e) {
            logger.warning("Failed to copy DimensionType components: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveEnum(Class<?> enumClass, String name) {
        if (!enumClass.isEnum()) {
            logger.warning("Expected enum type for " + name + " but got " + enumClass.getName());
            return null;
        }
        return Enum.valueOf((Class<Enum>) enumClass, name);
    }

    /**
     * Create a MonsterSettings record with constant light level and block light limit.
     */
    private Object createMonsterSettings(int lightLevel, int blockLightLimit) throws Exception {
        // Find the MonsterSettings inner class
        Class<?> msClass = null;
        for (Class<?> inner : dimTypeClass.getDeclaredClasses()) {
            if (inner.getSimpleName().equals("MonsterSettings")) {
                msClass = inner;
                break;
            }
        }
        if (msClass == null) {
            throw new RuntimeException("Cannot find MonsterSettings inner class");
        }

        // MonsterSettings(IntProvider monsterSpawnLightTest, int monsterSpawnBlockLightLimit)
        Constructor<?> msCtor = msClass.getDeclaredConstructors()[0];
        Class<?>[] paramTypes = msCtor.getParameterTypes();

        // First param is IntProvider — create a ConstantInt
        Object intProvider = createConstantInt(paramTypes[0], lightLevel);
        return msCtor.newInstance(intProvider, blockLightLimit);
    }

    /**
     * Create a ConstantInt (IntProvider subclass) for the given value.
     */
    private Object createConstantInt(Class<?> intProviderClass, int value) throws Exception {
        // Look for ConstantInt subclass with static of(int) method
        // ConstantInt is in net.minecraft.util.valueproviders
        String pkg = intProviderClass.getPackageName();
        Class<?> constantIntClass;
        try {
            constantIntClass = Class.forName(pkg + ".ConstantInt");
        } catch (ClassNotFoundException e) {
            // Try alternate naming
            constantIntClass = Class.forName("net.minecraft.util.valueproviders.ConstantInt");
        }
        Method ofMethod = constantIntClass.getMethod("of", int.class);
        return ofMethod.invoke(null, value);
    }

    /**
     * Resolve all reflection targets once. Returns true if NMS is available.
     */
    private boolean ensureResolved() {
        if (resolved) return available;
        resolved = true;
        try {
            // 1. Find CraftWorld.getHandle() -> ServerLevel
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            getHandleMethod = craftWorldClass.getMethod("getHandle");
            Class<?> serverLevelClass = getHandleMethod.getReturnType();

            // 2. Find DimensionType class via dimensionType() method
            Method dimTypeMethod = findMethod(serverLevelClass, "dimensionType");
            if (dimTypeMethod == null) {
                throw new RuntimeException("Cannot find dimensionType() method");
            }
            dimTypeClass = dimTypeMethod.getReturnType();

            // 3. Verify it's a record and get components
            if (!dimTypeClass.isRecord()) {
                throw new RuntimeException("DimensionType is not a record: " + dimTypeClass.getName());
            }
            dimTypeComponents = dimTypeClass.getRecordComponents();
            dimTypeConstructor = dimTypeClass.getDeclaredConstructors()[0];
            dimTypeConstructor.setAccessible(true);

            // 4. Find the dimensionTypeRegistration field (Holder<DimensionType>) on Level
            // Walk up the class hierarchy to find it
            Class<?> levelClass = serverLevelClass;
            dimensionTypeField = null;
            while (levelClass != null && dimensionTypeField == null) {
                for (Field f : levelClass.getDeclaredFields()) {
                    // The field type should be a Holder parameterized with DimensionType
                    String typeName = f.getGenericType().toString();
                    if (typeName.contains("Holder") && typeName.contains(dimTypeClass.getSimpleName())) {
                        dimensionTypeField = f;
                        dimensionTypeField.setAccessible(true);
                        break;
                    }
                }
                levelClass = levelClass.getSuperclass();
            }
            if (dimensionTypeField == null) {
                throw new RuntimeException("Cannot find Holder<DimensionType> field on Level class");
            }

            // 5. Find Holder.direct() static method
            Class<?> holderClass = dimensionTypeField.getType();
            holderDirectMethod = holderClass.getMethod("direct", Object.class);

            available = true;
            logger.info("NMS dimension type reflection resolved successfully");
        } catch (Exception e) {
            available = false;
            logger.warning("NMS dimension type reflection unavailable: " + e.getMessage());
            logger.warning("Falling back to datapack-based dimension type overrides");
        }
        return available;
    }

    private Method findMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public boolean isAvailable() {
        return ensureResolved();
    }

    @FunctionalInterface
    private interface DimTypeFactory {
        Object create(Object currentDimType) throws Exception;
    }
}
