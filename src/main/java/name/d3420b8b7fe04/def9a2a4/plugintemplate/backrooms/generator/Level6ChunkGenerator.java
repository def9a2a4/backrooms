package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Level 6 — "The Removed Dimension"
 * Nether reactor core patterns tiled across the floor. 16x16 exhibit rooms
 * separated by nether brick corridors. Glowstone ceiling.
 * Graveyard of deleted Minecraft features. Peaceful and melancholy.
 */
public class Level6ChunkGenerator extends ChunkGenerator {

    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 8;
    private static final int AIR_MIN_Y = 8;
    private static final int CEILING_Y = 16;
    private static final int CEILING_MAX_Y = 22;

    // 16x16 rooms separated by 2-wide nether brick corridors = 18-block period
    // Approximate with chunk alignment: each chunk is one room
    private static final int CORRIDOR_WIDTH = 2;

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Floor base
        chunkData.setRegion(0, FLOOR_Y, 0, 16, FLOOR_HEIGHT, 16, Material.COBBLESTONE);

        // Ceiling: glowstone
        chunkData.setRegion(0, CEILING_Y, 0, 16, CEILING_MAX_Y, 16, Material.GLOWSTONE);

        // Determine if this chunk is a room or corridor
        // Every other chunk is a corridor
        boolean isCorridorX = (Math.floorMod(chunkX, 2) == 1);
        boolean isCorridorZ = (Math.floorMod(chunkZ, 2) == 1);

        if (isCorridorX || isCorridorZ) {
            // Nether brick corridor
            generateCorridor(chunkData, chunkRng, isCorridorX, isCorridorZ);
        } else {
            // Exhibit room
            generateRoom(chunkData, chunkRng, seed, chunkX, chunkZ);
        }
    }

    private void generateCorridor(ChunkData chunkData, Random rng, boolean isCorridorX, boolean isCorridorZ) {
        // Nether brick walls with air passage
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Corridor walls
                chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.NETHER_BRICKS);
                for (int y = AIR_MIN_Y; y < CEILING_Y; y++) {
                    if (isCorridorX && isCorridorZ) {
                        // Intersection: open
                        chunkData.setBlock(x, y, z, Material.AIR);
                    } else if (isCorridorX) {
                        // X-aligned corridor: open in middle Z
                        if (z >= 4 && z <= 11) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        } else {
                            chunkData.setBlock(x, y, z, Material.NETHER_BRICKS);
                        }
                    } else {
                        // Z-aligned corridor: open in middle X
                        if (x >= 4 && x <= 11) {
                            chunkData.setBlock(x, y, z, Material.AIR);
                        } else {
                            chunkData.setBlock(x, y, z, Material.NETHER_BRICKS);
                        }
                    }
                }
            }
        }

        // Soul torches along walls
        for (int i = 2; i < 14; i += 4) {
            if (isCorridorX && !isCorridorZ) {
                chunkData.setBlock(i, AIR_MIN_Y, 4, Material.SOUL_TORCH);
                chunkData.setBlock(i, AIR_MIN_Y, 11, Material.SOUL_TORCH);
            }
            if (isCorridorZ && !isCorridorX) {
                chunkData.setBlock(4, AIR_MIN_Y, i, Material.SOUL_TORCH);
                chunkData.setBlock(11, AIR_MIN_Y, i, Material.SOUL_TORCH);
            }
        }
    }

    private void generateRoom(ChunkData chunkData, Random rng, long seed, int chunkX, int chunkZ) {
        // Clear air space
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = AIR_MIN_Y; y < CEILING_Y; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
            }
        }

        // Nether brick border frame around room edges
        for (int x = 0; x < 16; x++) {
            chunkData.setBlock(x, AIR_MIN_Y, 0, Material.NETHER_BRICKS);
            chunkData.setBlock(x, AIR_MIN_Y, 15, Material.NETHER_BRICKS);
        }
        for (int z = 0; z < 16; z++) {
            chunkData.setBlock(0, AIR_MIN_Y, z, Material.NETHER_BRICKS);
            chunkData.setBlock(15, AIR_MIN_Y, z, Material.NETHER_BRICKS);
        }

        // Sea lantern at room center for better lighting
        chunkData.setBlock(7, FLOOR_HEIGHT - 1, 7, Material.SEA_LANTERN);
        chunkData.setBlock(8, FLOOR_HEIGHT - 1, 8, Material.SEA_LANTERN);

        // Nether reactor core pattern on floor (3x3 cobblestone cross + gold corners)
        placeReactorCore(chunkData, 6, FLOOR_HEIGHT - 1, 6);

        // Room type based on chunk coordinates
        int roomType = Math.floorMod(chunkX * 7 + chunkZ * 13, 6);
        switch (roomType) {
            case 0 -> placeHumanMobExhibit(chunkData, rng);
            case 1 -> placeNetherReactorExhibit(chunkData, rng);
            case 2 -> placeSkyDimensionExhibit(chunkData, rng);
            case 3 -> placeCryingObsidianExhibit(chunkData, rng);
            case 4 -> placeEmptyExhibit(chunkData, rng);
            default -> placeOldCraftingExhibit(chunkData, rng);
        }

        // Doorways on all 4 sides (aligned with corridor openings)
        for (int y = AIR_MIN_Y; y < CEILING_Y; y++) {
            // N/S doors (x=6-9)
            for (int dx = 6; dx <= 9; dx++) {
                chunkData.setBlock(dx, y, 0, Material.AIR);
                chunkData.setBlock(dx, y, 15, Material.AIR);
            }
            // E/W doors (z=6-9)
            for (int dz = 6; dz <= 9; dz++) {
                chunkData.setBlock(0, y, dz, Material.AIR);
                chunkData.setBlock(15, y, dz, Material.AIR);
            }
        }
    }

    private void placeReactorCore(ChunkData chunkData, int cx, int y, int cz) {
        // Classic nether reactor core: cobblestone cross with gold corners
        Material cobble = Material.COBBLESTONE;
        Material gold = Material.GOLD_BLOCK;
        // Bottom layer 3x3
        chunkData.setBlock(cx, y, cz, cobble);
        chunkData.setBlock(cx + 1, y, cz, cobble);
        chunkData.setBlock(cx + 2, y, cz, cobble);
        chunkData.setBlock(cx, y, cz + 1, cobble);
        chunkData.setBlock(cx + 1, y, cz + 1, gold);
        chunkData.setBlock(cx + 2, y, cz + 1, cobble);
        chunkData.setBlock(cx, y, cz + 2, cobble);
        chunkData.setBlock(cx + 1, y, cz + 2, cobble);
        chunkData.setBlock(cx + 2, y, cz + 2, cobble);
    }

    private void placeHumanMobExhibit(ChunkData chunkData, Random rng) {
        // Mob spawner cage (iron bars around a space)
        for (int dx = 3; dx <= 5; dx++) {
            for (int dz = 3; dz <= 5; dz++) {
                chunkData.setBlock(dx, AIR_MIN_Y, dz, Material.IRON_BARS);
                chunkData.setBlock(dx, AIR_MIN_Y + 2, dz, Material.IRON_BARS);
            }
        }
        chunkData.setBlock(4, AIR_MIN_Y, 4, Material.AIR);
        chunkData.setBlock(4, AIR_MIN_Y + 1, 4, Material.AIR);
        chunkData.setBlock(4, AIR_MIN_Y + 2, 4, Material.AIR);
    }

    private void placeNetherReactorExhibit(ChunkData chunkData, Random rng) {
        // Full-size nether reactor structure
        // Layer 1 (floor): 3x3 cobble with gold corners
        for (int dx = 5; dx <= 9; dx++) {
            for (int dz = 5; dz <= 9; dz++) {
                boolean corner = (dx == 5 || dx == 9) && (dz == 5 || dz == 9);
                chunkData.setBlock(dx, AIR_MIN_Y, dz, corner ? Material.GOLD_BLOCK : Material.COBBLESTONE);
            }
        }
        // Layer 2: cross with reactor core (using lodestone as stand-in)
        chunkData.setBlock(7, AIR_MIN_Y + 1, 7, Material.LODESTONE);
        chunkData.setBlock(6, AIR_MIN_Y + 1, 7, Material.COBBLESTONE);
        chunkData.setBlock(8, AIR_MIN_Y + 1, 7, Material.COBBLESTONE);
        chunkData.setBlock(7, AIR_MIN_Y + 1, 6, Material.COBBLESTONE);
        chunkData.setBlock(7, AIR_MIN_Y + 1, 8, Material.COBBLESTONE);
    }

    private void placeSkyDimensionExhibit(ChunkData chunkData, Random rng) {
        // Floating grass island
        for (int dx = 4; dx <= 10; dx++) {
            for (int dz = 4; dz <= 10; dz++) {
                if (Math.abs(dx - 7) + Math.abs(dz - 7) <= 4) {
                    chunkData.setBlock(dx, AIR_MIN_Y + 3, dz, Material.GRASS_BLOCK);
                    chunkData.setBlock(dx, AIR_MIN_Y + 2, dz, Material.DIRT);
                }
            }
        }
        // Small oak tree on top
        chunkData.setBlock(7, AIR_MIN_Y + 4, 7, Material.OAK_LOG);
        chunkData.setBlock(7, AIR_MIN_Y + 5, 7, Material.OAK_LOG);
        chunkData.setBlock(7, AIR_MIN_Y + 6, 7, Material.OAK_LEAVES);
        chunkData.setBlock(6, AIR_MIN_Y + 5, 7, Material.OAK_LEAVES);
        chunkData.setBlock(8, AIR_MIN_Y + 5, 7, Material.OAK_LEAVES);
        chunkData.setBlock(7, AIR_MIN_Y + 5, 6, Material.OAK_LEAVES);
        chunkData.setBlock(7, AIR_MIN_Y + 5, 8, Material.OAK_LEAVES);
    }

    private void placeCryingObsidianExhibit(ChunkData chunkData, Random rng) {
        // Ring of crying obsidian
        for (int dx = 4; dx <= 10; dx++) {
            chunkData.setBlock(dx, AIR_MIN_Y, 4, Material.CRYING_OBSIDIAN);
            chunkData.setBlock(dx, AIR_MIN_Y, 10, Material.CRYING_OBSIDIAN);
        }
        for (int dz = 5; dz <= 9; dz++) {
            chunkData.setBlock(4, AIR_MIN_Y, dz, Material.CRYING_OBSIDIAN);
            chunkData.setBlock(10, AIR_MIN_Y, dz, Material.CRYING_OBSIDIAN);
        }
        // Respawn anchor in center
        chunkData.setBlock(7, AIR_MIN_Y, 7, Material.RESPAWN_ANCHOR);
    }

    private void placeEmptyExhibit(ChunkData chunkData, Random rng) {
        // The "[RESERVED] return_home.class" exhibit — just a cobblestone block
        chunkData.setBlock(7, AIR_MIN_Y, 7, Material.COBBLESTONE);
    }

    private void placeOldCraftingExhibit(ChunkData chunkData, Random rng) {
        // Old crafting items scattered
        chunkData.setBlock(5, AIR_MIN_Y, 5, Material.CRAFTING_TABLE);
        chunkData.setBlock(9, AIR_MIN_Y, 5, Material.FURNACE);
        chunkData.setBlock(5, AIR_MIN_Y, 9, Material.CHEST);
        chunkData.setBlock(9, AIR_MIN_Y, 9, Material.BOOKSHELF);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.THE_VOID);
            }
        };
    }
}
