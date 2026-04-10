package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for all backrooms chunk generators.
 * Handles custom biome resolution via the datapack registry.
 */
public abstract class BackroomsChunkGenerator extends ChunkGenerator {

    private final @Nullable NamespacedKey biomeKey;

    protected BackroomsChunkGenerator(@Nullable NamespacedKey biomeKey) {
        this.biomeKey = biomeKey;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        Biome biome = resolveBiome();
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return biome;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(biome);
            }
        };
    }

    private Biome resolveBiome() {
        if (biomeKey == null) {
            return Biome.THE_VOID;
        }
        try {
            Biome biome = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.BIOME)
                    .get(biomeKey);
            if (biome != null) {
                return biome;
            }
        } catch (Exception ignored) {
        }
        return Biome.THE_VOID;
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
