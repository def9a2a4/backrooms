package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import org.bukkit.NamespacedKey;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Maps string IDs to ChunkGenerator factories.
 * Generators are code — everything else about a level is config.
 */
public class GeneratorRegistry {

    private final Map<String, Function<NamespacedKey, ChunkGenerator>> generators = new HashMap<>();

    public void register(String id, Function<NamespacedKey, ChunkGenerator> factory) {
        generators.put(id, factory);
    }

    @Nullable
    public ChunkGenerator create(String id, @Nullable NamespacedKey biomeKey) {
        Function<NamespacedKey, ChunkGenerator> factory = generators.get(id);
        return factory != null ? factory.apply(biomeKey) : null;
    }

    public boolean has(String id) {
        return generators.containsKey(id);
    }

    public Set<String> getIds() {
        return generators.keySet();
    }

    /**
     * Register all built-in generators.
     */
    public void registerDefaults() {
        register("level_0", Level0ChunkGenerator::new);
        register("level_1", Level1ChunkGenerator::new);
        register("level_2", Level2ChunkGenerator::new);
        register("level_3", Level3ChunkGenerator::new);
        register("level_4", Level4ChunkGenerator::new);
        register("level_5", Level5ChunkGenerator::new);
        register("level_6", Level6ChunkGenerator::new);
        register("level_7", Level7ChunkGenerator::new);
        register("level_37", Level37ChunkGenerator::new);
        register("level_94", Level94ChunkGenerator::new);
        // level_64637 is registered in BackroomsPlugin after loading book config
    }
}
