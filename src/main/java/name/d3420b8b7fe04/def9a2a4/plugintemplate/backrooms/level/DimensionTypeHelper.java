package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.level;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Looks up datapack-registered dimension types (backrooms:dark, backrooms:light)
 * from the registry and swaps the holder on each backrooms world's Level.
 * The dimension types themselves are defined in the bundled datapack JSON files.
 */
public class DimensionTypeHelper {

    private final Logger logger;

    private boolean resolved = false;
    private boolean available = false;
    private Field dimensionTypeField;

    // Holders looked up from the registry (registered by the datapack)
    private Object darkHolder;
    private Object lightHolder;
    private Object fullbrightHolder;
    private Object brightBlackCrimsonHolder;
    private Object dayHolder;

    public DimensionTypeHelper(Logger logger) {
        this.logger = logger;
    }

    public boolean applyDarkDimension(World world) {
        ensureResolved();
        return applyDimension(world, "dark", darkHolder);
    }

    public boolean applyLightDimension(World world) {
        ensureResolved();
        return applyDimension(world, "light", lightHolder);
    }

    public boolean applyFullbrightDimension(World world) {
        ensureResolved();
        return applyDimension(world, "fullbright", fullbrightHolder);
    }

    public boolean applyBrightBlackCrimsonDimension(World world) {
        ensureResolved();
        return applyDimension(world, "bright_black_crimson", brightBlackCrimsonHolder);
    }

    public boolean applyDayDimension(World world) {
        ensureResolved();
        return applyDimension(world, "day", dayHolder);
    }

    private boolean applyDimension(World world, String label, Object holder) {
        if (!ensureResolved()) return false;
        if (holder == null) {
            logger.warning("No " + label + " holder available — datapack dimension type not loaded?");
            return false;
        }
        try {
            Object serverLevel = world.getClass().getMethod("getHandle").invoke(world);
            dimensionTypeField.set(serverLevel, holder);
            logger.info("Applied " + label + " dimension type to world: " + world.getName());
            return true;
        } catch (Exception e) {
            logger.warning("Dimension type swap failed for " + world.getName()
                    + " (" + label + "): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean ensureResolved() {
        if (resolved) return available;
        resolved = true;
        try {
            // 1. Get ServerLevel class
            Class<?> craftWorldClass = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Method getHandleMethod = craftWorldClass.getMethod("getHandle");
            Class<?> serverLevelClass = getHandleMethod.getReturnType();

            // 2. Find DimensionType class via dimensionType() method
            Method dimTypeMethod = findMethod(serverLevelClass, "dimensionType");
            if (dimTypeMethod == null) {
                throw new RuntimeException("Cannot find dimensionType() method on ServerLevel");
            }
            Class<?> dimTypeClass = dimTypeMethod.getReturnType();

            // 3. Find the Holder<DimensionType> field on Level
            dimensionTypeField = findHolderField(serverLevelClass, dimTypeClass);
            if (dimensionTypeField == null) {
                throw new RuntimeException("Cannot find Holder<DimensionType> field on Level");
            }

            // 4. Look up our datapack-registered dimension types from the registry
            lookupDatapackHolders();

            available = true;
            logger.info("Dimension type helper resolved (dark=" + (darkHolder != null)
                    + ", light=" + (lightHolder != null)
                    + ", fullbright=" + (fullbrightHolder != null)
                    + ", bright_black_crimson=" + (brightBlackCrimsonHolder != null)
                    + ", day=" + (dayHolder != null) + ")");
        } catch (Exception e) {
            available = false;
            logger.warning("Dimension type helper failed: " + e.getMessage());
            e.printStackTrace();
        }
        return available;
    }

    /**
     * Look up backrooms:dark and backrooms:light from the dimension type registry.
     * These are registered by the bundled datapack's dimension_type JSONs.
     */
    private void lookupDatapackHolders() throws Exception {
        // Get MinecraftServer
        Object craftServer = Bukkit.getServer();
        Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);

        // Get registryAccess
        Method registryAccessMethod = findMethod(minecraftServer.getClass(), "registryAccess");
        if (registryAccessMethod == null) {
            throw new RuntimeException("Cannot find registryAccess() method");
        }
        Object registryAccess = registryAccessMethod.invoke(minecraftServer);

        // Get Registries.DIMENSION_TYPE key
        Class<?> registriesClass = Class.forName("net.minecraft.core.registries.Registries");
        Object dimTypeRegistryKey = registriesClass.getField("DIMENSION_TYPE").get(null);

        // Look up the registry
        Method registryMethod = findRegistryMethod(registryAccess);
        if (registryMethod == null) {
            throw new RuntimeException("Cannot find registry lookup method");
        }
        Object registry = registryMethod.invoke(registryAccess, dimTypeRegistryKey);
        if (registry != null && registry.getClass().getName().equals("java.util.Optional")) {
            registry = registry.getClass().getMethod("orElseThrow").invoke(registry);
        }

        // Derive ResourceLocation and ResourceKey classes from existing objects
        Class<?> resourceKeyClass = dimTypeRegistryKey.getClass();
        Class<?> resourceLocationClass = deriveResourceLocationClass(resourceKeyClass, dimTypeRegistryKey);

        // Find ResourceLocation factory and ResourceKey.create
        Method fromNsAndPath = findStaticFactory(resourceLocationClass);
        Method createKey = findCreateKeyMethod(resourceKeyClass);

        // Create resource keys for our dimension types
        Object darkLocation = fromNsAndPath.invoke(null, "backrooms", "dark");
        Object lightLocation = fromNsAndPath.invoke(null, "backrooms", "light");
        Object fullbrightLocation = fromNsAndPath.invoke(null, "backrooms", "fullbright");
        Object bbcLocation = fromNsAndPath.invoke(null, "backrooms", "bright_black_crimson");
        Object dayLocation = fromNsAndPath.invoke(null, "backrooms", "day");
        Object darkKey = createKey.invoke(null, dimTypeRegistryKey, darkLocation);
        Object lightKey = createKey.invoke(null, dimTypeRegistryKey, lightLocation);
        Object fullbrightKey = createKey.invoke(null, dimTypeRegistryKey, fullbrightLocation);
        Object bbcKey = createKey.invoke(null, dimTypeRegistryKey, bbcLocation);
        Object dayKey = createKey.invoke(null, dimTypeRegistryKey, dayLocation);

        // Look up holders: registry.getHolder(ResourceKey) -> Optional<Holder.Reference>
        Method getHolderMethod = findGetHolderMethod(registry, resourceKeyClass);
        if (getHolderMethod != null) {
            darkHolder = unwrapOptional(getHolderMethod.invoke(registry, darkKey));
            lightHolder = unwrapOptional(getHolderMethod.invoke(registry, lightKey));
            fullbrightHolder = unwrapOptional(getHolderMethod.invoke(registry, fullbrightKey));
            brightBlackCrimsonHolder = unwrapOptional(getHolderMethod.invoke(registry, bbcKey));
            dayHolder = unwrapOptional(getHolderMethod.invoke(registry, dayKey));
        }

        if (darkHolder == null || lightHolder == null || fullbrightHolder == null
                || brightBlackCrimsonHolder == null || dayHolder == null) {
            // Dump registered keys for debugging
            StringBuilder sb = new StringBuilder("Registered dimension types: ");
            try {
                Method keySetMethod = findMethod(registry.getClass(), "keySet");
                if (keySetMethod == null) keySetMethod = findMethod(registry.getClass(), "registryKeySet");
                if (keySetMethod != null) {
                    Object keySet = keySetMethod.invoke(registry);
                    sb.append(keySet);
                }
            } catch (Exception ignored) {}
            throw new RuntimeException("One or more backrooms dimension types not found in dimension type registry. "
                    + sb + ". Is the datapack loaded?");
        }
    }

    // --- Reflection helpers ---

    private Field findHolderField(Class<?> startClass, Class<?> dimTypeClass) {
        Class<?> current = startClass;
        while (current != null) {
            for (Field f : current.getDeclaredFields()) {
                String typeName = f.getGenericType().toString();
                if (typeName.contains("Holder") && typeName.contains(dimTypeClass.getSimpleName())) {
                    f.setAccessible(true);
                    logger.info("Found dimension type field: " + f.getName()
                            + " on " + current.getSimpleName());
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private Method findRegistryMethod(Object registryAccess) {
        for (String name : new String[]{"registryOrThrow", "lookupOrThrow", "registry"}) {
            for (Method m : registryAccess.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        return null;
    }

    private Class<?> deriveResourceLocationClass(Class<?> resourceKeyClass, Object resourceKeyInstance) throws Exception {
        // Scan all zero-param methods returning a non-JDK, non-ResourceKey type
        for (Method m : resourceKeyClass.getMethods()) {
            if (m.getParameterCount() == 0
                    && !m.getReturnType().isPrimitive()
                    && !m.getReturnType().getName().startsWith("java.")
                    && !m.getName().equals("getClass")
                    && m.getReturnType() != resourceKeyClass) {
                Object result = m.invoke(resourceKeyInstance);
                if (result != null) {
                    return result.getClass();
                }
            }
        }
        throw new RuntimeException("Cannot derive ResourceLocation class from ResourceKey");
    }

    private Method findStaticFactory(Class<?> resourceLocationClass) {
        for (Method m : resourceLocationClass.getMethods()) {
            if (m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == String.class
                    && m.getParameterTypes()[1] == String.class
                    && m.getReturnType() == resourceLocationClass) {
                return m;
            }
        }
        throw new RuntimeException("Cannot find ResourceLocation factory method");
    }

    private Method findCreateKeyMethod(Class<?> resourceKeyClass) {
        for (Method m : resourceKeyClass.getMethods()) {
            if (m.getName().equals("create") && m.getParameterCount() == 2) {
                return m;
            }
        }
        throw new RuntimeException("Cannot find ResourceKey.create() method");
    }

    private Method findGetHolderMethod(Object registry, Class<?> resourceKeyClass) {
        // Try getHolder(ResourceKey) -> Optional<Holder.Reference>
        for (String name : new String[]{"getHolder", "getHolderOrThrow", "get", "wrapAsHolder"}) {
            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(resourceKeyClass)) {
                    return m;
                }
            }
        }
        return null;
    }

    private Object unwrapOptional(Object result) throws Exception {
        if (result == null) return null;
        if (result.getClass().getName().equals("java.util.Optional")) {
            Method isPresent = result.getClass().getMethod("isPresent");
            if ((boolean) isPresent.invoke(result)) {
                return result.getClass().getMethod("get").invoke(result);
            }
            return null;
        }
        return result;
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
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    public boolean isAvailable() {
        return ensureResolved();
    }
}
