package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.noise.SimplexNoise;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Level 1 — "The Habitable Zone"
 * Three zone types determined by low-frequency noise:
 *   WAREHOUSE — open parking-garage spaces with pillars, beams, lights (default)
 *   CORRIDOR  — tight hallway networks carved from solid stone
 *   GARDEN    — small overgrown patches with moss, vines, glow berries
 */
public class Level1ChunkGenerator extends BackroomsChunkGenerator {

    public Level1ChunkGenerator(NamespacedKey biomeKey) {
        super(biomeKey);
    }

    private enum Zone { WAREHOUSE, CORRIDOR, GARDEN }

    // Y layout
    private static final int FLOOR_Y = 0;
    private static final int FLOOR_HEIGHT = 10;
    private static final int AIR_MIN_Y = 10;
    private static final int CEILING_MIN_Y = 18;
    private static final int CEILING_MAX_Y = 30;

    // Warehouse cell grid
    private static final int CELL_SIZE = 8;
    private static final int CELLS_PER_AXIS = 16 / CELL_SIZE;
    private static final double REGION_SCALE = 1.0 / 48.0;

    // Lighting
    public static final int LIGHT_SPACING = 11;

    // Material noise
    private static final double MATERIAL_SCALE = 0.02;
    private static final double ACCENT_SCALE = 0.15;

    // Zone noise
    private static final double CORRIDOR_ZONE_SCALE = 0.01; // low freq = larger zones
    private static final double GARDEN_ZONE_SCALE = 0.004;  // lower = larger patches
    private static final double MIN_GARDEN_DISTANCE = 300.0; // blocks from origin

    // Corridor constants
    private static final int CORRIDOR_PERIOD = 7;
    private static final double CORRIDOR_LINE_SCALE = 0.02;
    private static final double CORRIDOR_CEILING_SCALE = 1.0 / 24.0;

    // Beam constants
    private static final int BEAM_SPACING = 5;

    private static final Material[] PILLAR_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.POLISHED_ANDESITE
    };

    private static final Material[] WALL_MATERIALS = {
            Material.SMOOTH_STONE, Material.STONE, Material.STONE_BRICKS,
            Material.ANDESITE
    };

    private static final Material[] BEAM_MATERIALS = {
            Material.STONE_BRICKS, Material.SMOOTH_STONE, Material.POLISHED_ANDESITE
    };

    // Garden palettes
    private static final Material[] GARDEN_MATERIALS = {
            Material.MOSS_BLOCK, Material.MOSSY_COBBLESTONE, Material.MOSSY_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.PALE_MOSS_BLOCK
    };

    private static final Material[] GARDEN_COPPER = {
            Material.WAXED_WEATHERED_COPPER, Material.WAXED_WEATHERED_CUT_COPPER,
            Material.WAXED_WEATHERED_COPPER_GRATE, Material.WAXED_WEATHERED_CHISELED_COPPER,
            Material.WAXED_WEATHERED_COPPER_BULB
    };

    private static final Material[] LEAF_TYPES = {
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.DARK_OAK_LEAVES, Material.AZALEA_LEAVES
    };

    // ── Zone selection ──────────────────────────────────────────────────

    private Zone getZone(long seed, int worldX, int worldZ) {
        // Garden: large rare blobs (~4%), suppressed near origin
        double distSq = (double) worldX * worldX + (double) worldZ * worldZ;
        if (distSq >= MIN_GARDEN_DISTANCE * MIN_GARDEN_DISTANCE) {
            double gardenNoise = SimplexNoise.noise2(seed + 50,
                    worldX * GARDEN_ZONE_SCALE, worldZ * GARDEN_ZONE_SCALE);
            if (gardenNoise > 0.73) return Zone.GARDEN;
        }

        // Corridor: large connected zones (~19%)
        double corridorNoise = SimplexNoise.noise2(seed + 51,
                worldX * CORRIDOR_ZONE_SCALE, worldZ * CORRIDOR_ZONE_SCALE);
        if (corridorNoise > 0.41) return Zone.CORRIDOR;

        return Zone.WAREHOUSE;  // ~80%
    }

    // ── Main generation ─────────────────────────────────────────────────

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        Random chunkRng = new Random(seed ^ ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L));

        // Pass 1: Per-column floor, ceiling, and zone-specific fill
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                Zone zone = getZone(seed, worldX, worldZ);

                switch (zone) {
                    case WAREHOUSE -> generateWarehouseColumn(chunkData, seed, x, z, worldX, worldZ);
                    case CORRIDOR -> generateCorridorColumn(chunkData, seed, x, z, worldX, worldZ);
                    case GARDEN -> generateGardenColumn(chunkData, seed, chunkRng, x, z, worldX, worldZ);
                }
            }
        }

        // Pass 2: Water drip (~20% of chunks)
        if (chunkRng.nextInt(5) == 0) {
            int wx = chunkRng.nextInt(16);
            int wz = chunkRng.nextInt(16);
            int worldX = chunkX * 16 + wx;
            int worldZ = chunkZ * 16 + wz;
            if (getZone(seed, worldX, worldZ) == Zone.WAREHOUSE) {
                chunkData.setBlock(wx, CEILING_MIN_Y, wz, Material.WATER);
            }
        }

        // Pass 3: Structures (pillars, walls) — warehouse and garden zones
        for (int cx = 0; cx < CELLS_PER_AXIS; cx++) {
            for (int cz = 0; cz < CELLS_PER_AXIS; cz++) {
                int baseX = cx * CELL_SIZE;
                int baseZ = cz * CELL_SIZE;

                int cellWorldX = chunkX * 16 + baseX + CELL_SIZE / 2;
                int cellWorldZ = chunkZ * 16 + baseZ + CELL_SIZE / 2;
                Zone cellZone = getZone(seed, cellWorldX, cellWorldZ);
                if (cellZone == Zone.CORRIDOR) continue;

                double worldCenterX = cellWorldX * REGION_SCALE;
                double worldCenterZ = cellWorldZ * REGION_SCALE;
                double noise = SimplexNoise.noise2(seed, worldCenterX, worldCenterZ);
                double detail = SimplexNoise.noise2(seed + 3, worldCenterX * 3, worldCenterZ * 3);

                boolean gardenPalette = cellZone == Zone.GARDEN;
                placeStructure(chunkData, chunkRng, baseX, baseZ, noise, detail, gardenPalette);
            }
        }

        // Pass 4: Support beams (warehouse only)
        placeBeams(chunkData, seed, chunkX, chunkZ);

        // Pass 5: Ceiling lights — warehouse and garden zones (same grid)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) == Zone.CORRIDOR) continue;
                if (worldX % LIGHT_SPACING == 0 && worldZ % LIGHT_SPACING == 0) {
                    chunkData.setBlock(x, CEILING_MIN_Y, z, Material.VERDANT_FROGLIGHT);
                }
            }
        }

        // Pass 6: Garden floor decorations
        placeGardenDecorations(chunkData, seed, chunkX, chunkZ, chunkRng);

        // Bedrock boundaries: 8-block floor, 10-block ceiling (onlySolid to avoid corridor airspace)
        applyBoundaryLayer(chunkData, FLOOR_Y, FLOOR_Y + 8, Material.BEDROCK, false);
        applyBoundaryLayer(chunkData, CEILING_MAX_Y - 10, CEILING_MAX_Y, Material.BEDROCK, true);

        // Pass 7: Rare garden exit structures (deep pool, ceiling breach)
        // Must run AFTER bedrock boundaries so breaches aren't overwritten
        placeGardenExits(chunkData, seed, chunkX, chunkZ);
    }

    // ── Warehouse column (existing behavior) ────────────────────────────

    private void generateWarehouseColumn(ChunkData chunkData, long seed, int x, int z,
                                         int worldX, int worldZ) {
        Material baseMat = getRegionMaterial(seed, worldX, worldZ);
        Material accentedMat = applyAccent(seed, worldX, worldZ, baseMat);

        // Sub-floor fill + surface
        chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, baseMat);
        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, accentedMat);

        // Ceiling
        chunkData.setRegion(x, CEILING_MIN_Y, z, x + 1, CEILING_MAX_Y, z + 1, baseMat);
        chunkData.setBlock(x, CEILING_MIN_Y, z, accentedMat);
    }

    // ── Corridor column ─────────────────────────────────────────────────

    private void generateCorridorColumn(ChunkData chunkData, long seed, int x, int z,
                                        int worldX, int worldZ) {
        Material baseMat = getRegionMaterial(seed, worldX, worldZ);
        Material accentedMat = applyAccent(seed, worldX, worldZ, baseMat);

        // Floor (same as warehouse)
        chunkData.setRegion(x, FLOOR_Y, z, x + 1, FLOOR_HEIGHT, z + 1, baseMat);
        chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, accentedMat);

        // Upper ceiling (above CEILING_MIN_Y) is always solid
        chunkData.setRegion(x, CEILING_MIN_Y, z, x + 1, CEILING_MAX_Y, z + 1, baseMat);

        // Determine local corridor ceiling height (lower than warehouse)
        double ceilNoise = SimplexNoise.noise2(seed + 55, worldX * CORRIDOR_CEILING_SCALE, worldZ * CORRIDOR_CEILING_SCALE);
        int localCeilingY = (ceilNoise < -0.3) ? 13 : (ceilNoise < 0.3) ? 14 : 15;

        // Default: fill air space with solid material (will carve hallways below)
        chunkData.setRegion(x, AIR_MIN_Y, z, x + 1, CEILING_MIN_Y, z + 1, baseMat);

        // Check if this column is on an active hallway line
        boolean onEW = isOnCorridorLine(seed, worldX, worldZ, true);
        boolean onNS = isOnCorridorLine(seed, worldX, worldZ, false);

        if (onEW || onNS) {
            // Carve air for the hallway
            for (int y = AIR_MIN_Y; y < localCeilingY; y++) {
                chunkData.setBlock(x, y, z, Material.AIR);
            }
            // Ceiling surface at local height
            chunkData.setBlock(x, localCeilingY, z, accentedMat);

            // Corridor lights at grid intersections
            if (worldX % CORRIDOR_PERIOD == 0 && worldZ % CORRIDOR_PERIOD == 0) {
                chunkData.setBlock(x, localCeilingY, z, Material.VERDANT_FROGLIGHT);
            }
        }
    }

    private boolean isOnCorridorLine(long seed, int worldX, int worldZ, boolean eastWest) {
        // East-West lines run along X, placed on Z grid
        // North-South lines run along Z, placed on X grid
        int lineCoord = eastWest ? worldZ : worldX;
        int posInPeriod = Math.floorMod(lineCoord, CORRIDOR_PERIOD);

        // Determine width of this line (2 or 3 blocks)
        int lineIndex = Math.floorDiv(lineCoord, CORRIDOR_PERIOD);
        double widthNoise = SimplexNoise.noise2(seed + (eastWest ? 60 : 61), lineIndex * 0.3, 0);
        int lineWidth = widthNoise > 0.2 ? 3 : 2;

        if (posInPeriod >= lineWidth) return false;

        // Check if this line is active (~70%)
        double activeNoise = SimplexNoise.noise2(seed + (eastWest ? 62 : 63), lineIndex * 0.15, 0);
        return activeNoise > 0.0;
    }

    // ── Garden column ───────────────────────────────────────────────────

    private Material randomGardenBlock(Random rng) {
        // ~8% chance of weathered copper accent
        if (rng.nextInt(12) == 0) {
            return GARDEN_COPPER[rng.nextInt(GARDEN_COPPER.length)];
        }
        return GARDEN_MATERIALS[rng.nextInt(GARDEN_MATERIALS.length)];
    }

    private void generateGardenColumn(ChunkData chunkData, long seed, Random chunkRng,
                                      int x, int z, int worldX, int worldZ) {
        // Each block independently random from the garden palette
        for (int y = FLOOR_Y; y < FLOOR_HEIGHT; y++) {
            chunkData.setBlock(x, y, z, randomGardenBlock(chunkRng));
        }

        // Ceiling: each block independent
        for (int y = CEILING_MIN_Y; y < CEILING_MAX_Y; y++) {
            chunkData.setBlock(x, y, z, randomGardenBlock(chunkRng));
        }

        // Spore blossom hidden one block into the ceiling (~1/64)
        if (chunkRng.nextInt(64) == 0) {
            chunkData.setBlock(x, CEILING_MIN_Y, z, Material.SPORE_BLOSSOM);
        }

        // Hanging vegetation from ceiling (iid random per column)
        int hangRoll = chunkRng.nextInt(100);
        if (hangRoll < 2) {
            // ~2% glow berry vines
            int vineLength = 2 + chunkRng.nextInt(3);
            for (int dy = 1; dy <= vineLength; dy++) {
                int y = CEILING_MIN_Y - dy;
                if (y <= AIR_MIN_Y) break;
                chunkData.setBlock(x, y, z, dy == vineLength ? Material.CAVE_VINES : Material.CAVE_VINES_PLANT);
            }
        } else if (hangRoll < 10) {
            // ~8% pale hanging moss
            int mossLen = 1 + chunkRng.nextInt(2);
            for (int dy = 1; dy <= mossLen; dy++) {
                int y = CEILING_MIN_Y - dy;
                if (y <= AIR_MIN_Y) break;
                chunkData.setBlock(x, y, z, Material.PALE_HANGING_MOSS);
            }
        } else if (hangRoll < 15) {
            // ~5% hanging roots
            chunkData.setBlock(x, CEILING_MIN_Y - 1, z, Material.HANGING_ROOTS);
        }
    }

    private BlockData persistentLeaf(Material leafMat) {
        return Bukkit.createBlockData(leafMat, "[persistent=true]");
    }

    private BlockData randomPersistentLeaf(Random rng) {
        return persistentLeaf(LEAF_TYPES[rng.nextInt(LEAF_TYPES.length)]);
    }

    private void placeGardenDecorations(ChunkData chunkData, long seed, int chunkX, int chunkZ, Random chunkRng) {
        // First pass: water pools (need adjacency spread within chunk)
        boolean[][] waterMap = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) != Zone.GARDEN) continue;
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                if (chunkRng.nextInt(20) == 0) {
                    waterMap[x][z] = true;
                    for (int s = 0; s < 2; s++) {
                        int nx = x + chunkRng.nextInt(3) - 1;
                        int nz = z + chunkRng.nextInt(3) - 1;
                        if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                            waterMap[nx][nz] = true;
                        }
                    }
                }
            }
        }

        // Second pass: floor decorations, ceiling leaf piles, dripstone
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) != Zone.GARDEN) continue;

                // Ceiling decorations — hanging leaf piles and dripstone
                if (chunkData.getType(x, CEILING_MIN_Y - 1, z) == Material.AIR) {
                    int ceilRoll = chunkRng.nextInt(50);
                    if (ceilRoll < 6) {
                        // ~12% ceiling hanging leaf piles (1-3 blocks down)
                        int height = 1 + chunkRng.nextInt(3);
                        for (int dy = 1; dy <= height; dy++) {
                            int y = CEILING_MIN_Y - dy;
                            if (y <= AIR_MIN_Y) break;
                            chunkData.setBlock(x, y, z, randomPersistentLeaf(chunkRng));
                        }
                    } else if (ceilRoll == 6) {
                        // ~2% ceiling dripstone (3-5 segments hanging)
                        int dripLen = 3 + chunkRng.nextInt(3);
                        for (int dy = 1; dy <= dripLen; dy++) {
                            int y = CEILING_MIN_Y - dy;
                            if (y <= AIR_MIN_Y) break;
                            String thickness = (dy == dripLen) ? "tip" : (dy == 1) ? "base" : "middle";
                            chunkData.setBlock(x, y, z, Bukkit.createBlockData(Material.POINTED_DRIPSTONE,
                                    "[vertical_direction=down,thickness=" + thickness + "]"));
                        }
                    }
                }

                // Floor decorations — skip pillars/walls
                if (chunkData.getType(x, AIR_MIN_Y, z) != Material.AIR) continue;

                if (waterMap[x][z]) {
                    chunkData.setBlock(x, FLOOR_HEIGHT - 1, z, Material.WATER);
                    continue;
                }

                int roll = chunkRng.nextInt(100);
                if (roll < 15) {
                    // ~15% moss carpet (mix of regular and pale)
                    chunkData.setBlock(x, AIR_MIN_Y, z,
                            chunkRng.nextBoolean() ? Material.MOSS_CARPET : Material.PALE_MOSS_CARPET);
                } else if (roll < 25) {
                    // ~10% leaf pile — 1-3 blocks tall, varied types
                    int height = 1 + chunkRng.nextInt(3);
                    for (int dy = 0; dy < height; dy++) {
                        int y = AIR_MIN_Y + dy;
                        if (y >= CEILING_MIN_Y) break;
                        chunkData.setBlock(x, y, z, randomPersistentLeaf(chunkRng));
                    }
                } else if (roll < 29) {
                    // ~4% wither rose
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.WITHER_ROSE);
                } else if (roll < 34) {
                    // ~5% warped roots
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.WARPED_ROOTS);
                } else if (roll < 38) {
                    // ~4% nether sprouts
                    chunkData.setBlock(x, AIR_MIN_Y, z, Material.NETHER_SPROUTS);
                } else if (roll < 41) {
                    // ~3% twisting vines (1-3 blocks upward)
                    int vineLen = 1 + chunkRng.nextInt(3);
                    for (int dy = 0; dy < vineLen; dy++) {
                        int y = AIR_MIN_Y + dy;
                        if (y >= CEILING_MIN_Y) break;
                        chunkData.setBlock(x, y, z,
                                dy == vineLen - 1 ? Material.TWISTING_VINES : Material.TWISTING_VINES_PLANT);
                    }
                } else if (roll == 41) {
                    // ~1% floor dripstone (3-5 segments pointing up)
                    int dripLen = 3 + chunkRng.nextInt(3);
                    for (int dy = 0; dy < dripLen; dy++) {
                        int y = AIR_MIN_Y + dy;
                        if (y >= CEILING_MIN_Y) break;
                        String thickness = (dy == dripLen - 1) ? "tip" : (dy == 0) ? "base" : "middle";
                        chunkData.setBlock(x, y, z, Bukkit.createBlockData(Material.POINTED_DRIPSTONE,
                                "[vertical_direction=up,thickness=" + thickness + "]"));
                    }
                }
            }
        }

        // Third pass: regular vines on the sides of leaf blocks (floor and ceiling)
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) != Zone.GARDEN) continue;

                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    if (!isLeafBlock(chunkData.getType(x, y, z))) continue;

                    // Try each horizontal direction independently (~25% per face)
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    String[] faces = {"west", "east", "north", "south"};
                    for (int d = 0; d < 4; d++) {
                        if (chunkRng.nextInt(4) != 0) continue;
                        int nx = x + dirs[d][0];
                        int nz = z + dirs[d][1];
                        if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16
                                && chunkData.getType(nx, y, nz) == Material.AIR) {
                            int vineLen = chunkRng.nextInt(5) + 1;
                            for (int v = 0; v < vineLen; v++) {
                                int vy = y - v;
                                if (vy <= AIR_MIN_Y) break;
                                if (chunkData.getType(nx, vy, nz) != Material.AIR) break;
                                chunkData.setBlock(nx, vy, nz, Bukkit.createBlockData(Material.VINE,
                                        "[" + faces[d] + "=true]"));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fourth pass: rare exit structures in garden zones.
     * - Deep sinkhole pool (water shaft below floor Y → L2 exit)
     * - Ceiling breach with vines (hole through ceiling → Skyblock exit)
     * Both use deterministic noise so they appear in fixed locations.
     */
    private void placeGardenExits(ChunkData chunkData, long seed, int chunkX, int chunkZ) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkX * 16 + x;
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) != Zone.GARDEN) continue;

                long exitHash = seed ^ ((long) worldX * 472882027L + (long) worldZ * 920419813L);

                // Floor breach — 1x1 water shaft down to world bottom
                if (Math.floorMod(exitHash, 200) == 0) {
                    for (int y = AIR_MIN_Y; y >= chunkData.getMinHeight(); y--) {
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                }

                // Ceiling breach — 1x1 hole through ceiling with climbable vines inside
                if (Math.floorMod(exitHash + 1, 200) == 0) {
                    for (int y = CEILING_MIN_Y; y < CEILING_MAX_Y; y++) {
                        chunkData.setBlock(x, y, z, Material.AIR);
                    }
                    // Cave vines from top of breach down to near floor
                    for (int y = CEILING_MAX_Y - 1; y >= AIR_MIN_Y + 1; y--) {
                        chunkData.setBlock(x, y, z,
                                y == AIR_MIN_Y + 1 ? Material.CAVE_VINES : Material.CAVE_VINES_PLANT);
                    }
                }
            }
        }
    }

    private static boolean isLeafBlock(Material mat) {
        for (Material leaf : LEAF_TYPES) {
            if (leaf == mat) return true;
        }
        return false;
    }

    // ── Material helpers ────────────────────────────────────────────────

    private Material getRegionMaterial(long seed, int worldX, int worldZ) {
        double noise = SimplexNoise.noise2(seed + 1, worldX * MATERIAL_SCALE, worldZ * MATERIAL_SCALE);
        if (noise < -0.2) {
            return Material.STONE;
        } else if (noise < 0.2) {
            return Material.SMOOTH_STONE;
        } else {
            return Material.STONE_BRICKS;
        }
    }

    private Material applyAccent(long seed, int worldX, int worldZ, Material baseMat) {
        double accentNoise = SimplexNoise.noise2(seed + 10, worldX * ACCENT_SCALE, worldZ * ACCENT_SCALE);
        if (baseMat == Material.STONE_BRICKS && accentNoise > 0.55) {
            return Material.CRACKED_STONE_BRICKS;
        }
        if (baseMat == Material.STONE && accentNoise > 0.6) {
            return Material.POLISHED_DIORITE;
        }
        return baseMat;
    }

    // ── Beams (warehouse only) ──────────────────────────────────────────

    private static boolean isBeamMaterial(Material mat) {
        for (Material m : BEAM_MATERIALS) {
            if (m == mat) return true;
        }
        return false;
    }

    private static boolean canPlaceBeam(ChunkData chunkData, int x, int y, int z) {
        Material existing = chunkData.getType(x, y, z);
        return existing == Material.AIR || isBeamMaterial(existing);
    }

    private void placeBeams(ChunkData chunkData, long seed, int chunkX, int chunkZ) {
        int beamTopY = CEILING_MIN_Y - 1;

        // Beams running along Z (placed on X grid lines)
        for (int x = 0; x < 16; x++) {
            int worldX = chunkX * 16 + x;
            if (worldX % BEAM_SPACING != 0) continue;

            double presenceNoise = SimplexNoise.noise2(seed + 20, worldX * 0.04, 0);
            if (presenceNoise < -0.1) continue;

            int matIndex = Math.abs((int) (presenceNoise * 1000)) % BEAM_MATERIALS.length;
            Material beamMat = BEAM_MATERIALS[matIndex];

            double sizeNoise = SimplexNoise.noise2(seed + 22, worldX * 0.08, 0);
            int beamWidth = sizeNoise > 0.3 ? 2 : 1;
            int beamHeight = sizeNoise < -0.3 ? 2 : 1;

            for (int z = 0; z < 16; z++) {
                int worldZ = chunkZ * 16 + z;
                if (getZone(seed, worldX, worldZ) != Zone.WAREHOUSE) continue;

                for (int dx = 0; dx < beamWidth && x + dx < 16; dx++) {
                    for (int dy = 0; dy < beamHeight; dy++) {
                        int py = beamTopY - dy;
                        if (canPlaceBeam(chunkData, x + dx, py, z)) {
                            chunkData.setBlock(x + dx, py, z, beamMat);
                        }
                    }
                }
            }
        }

        // Beams running along X (placed on Z grid lines)
        for (int z = 0; z < 16; z++) {
            int worldZ = chunkZ * 16 + z;
            if (worldZ % BEAM_SPACING != 0) continue;

            double presenceNoise = SimplexNoise.noise2(seed + 21, 0, worldZ * 0.04);
            if (presenceNoise < -0.1) continue;

            int matIndex = Math.abs((int) (presenceNoise * 1000)) % BEAM_MATERIALS.length;
            Material beamMat = BEAM_MATERIALS[matIndex];

            double sizeNoise = SimplexNoise.noise2(seed + 23, 0, worldZ * 0.08);
            int beamWidth = sizeNoise > 0.3 ? 2 : 1;
            int beamHeight = sizeNoise < -0.3 ? 2 : 1;

            for (int x = 0; x < 16; x++) {
                int worldX = chunkX * 16 + x;
                if (getZone(seed, worldX, worldZ) != Zone.WAREHOUSE) continue;

                for (int dz = 0; dz < beamWidth && z + dz < 16; dz++) {
                    for (int dy = 0; dy < beamHeight; dy++) {
                        int py = beamTopY - dy;
                        if (canPlaceBeam(chunkData, x, py, z + dz)) {
                            chunkData.setBlock(x, py, z + dz, beamMat);
                        }
                    }
                }
            }
        }
    }

    // ── Structures (pillars, walls) ─────────────────────────────────────

    private void placeStructure(ChunkData chunkData, Random rng, int baseX, int baseZ,
                                double noise, double detail, boolean gardenPalette) {
        if (noise < -0.2) {
            if (detail > 0.3) {
                placeThickPillar(chunkData, rng, baseX + 3, baseZ + 3, gardenPalette);
            }
        } else if (noise < 0.15) {
            placeThickPillar(chunkData, rng, baseX + 2, baseZ + 2, gardenPalette);
            if (detail > 0.2) {
                placeThickPillar(chunkData, rng, baseX + 5, baseZ + 5, gardenPalette);
            }
        } else if (noise < 0.4) {
            placeLowWall(chunkData, rng, baseX, baseZ, gardenPalette);
        } else {
            placeWallSection(chunkData, rng, baseX, baseZ, gardenPalette);
        }
    }

    private void placeThickPillar(ChunkData chunkData, Random rng, int x, int z, boolean gardenPalette) {
        if (x >= 16 || z >= 16) return;
        Material mat = gardenPalette ? randomGardenBlock(rng) : PILLAR_MATERIALS[rng.nextInt(PILLAR_MATERIALS.length)];
        for (int dx = 0; dx < 2 && x + dx < 16; dx++) {
            for (int dz = 0; dz < 2 && z + dz < 16; dz++) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x + dx, y, z + dz, gardenPalette ? randomGardenBlock(rng) : mat);
                }
            }
        }
    }

    private void placeLowWall(ChunkData chunkData, Random rng, int baseX, int baseZ, boolean gardenPalette) {
        Material mat = gardenPalette ? randomGardenBlock(rng) : WALL_MATERIALS[rng.nextInt(WALL_MATERIALS.length)];
        int dir = rng.nextInt(2);
        int height = AIR_MIN_Y + 2 + rng.nextInt(2);
        int offset = rng.nextInt(2);
        for (int i = 0; i < CELL_SIZE && baseX + (dir == 0 ? i : 0) < 16 && baseZ + (dir == 1 ? i : 0) < 16; i++) {
            int x = baseX + (dir == 0 ? i : offset);
            int z = baseZ + (dir == 1 ? i : offset);
            if (x < 16 && z < 16) {
                for (int y = AIR_MIN_Y; y < height; y++) {
                    chunkData.setBlock(x, y, z, gardenPalette ? randomGardenBlock(rng) : mat);
                }
            }
        }
    }

    private void placeWallSection(ChunkData chunkData, Random rng, int baseX, int baseZ, boolean gardenPalette) {
        Material mat = gardenPalette ? randomGardenBlock(rng) : WALL_MATERIALS[rng.nextInt(WALL_MATERIALS.length)];
        int dir = rng.nextInt(4);
        for (int i = 0; i < CELL_SIZE; i++) {
            int x, z;
            switch (dir) {
                case 0 -> { x = baseX + i; z = baseZ; }
                case 1 -> { x = baseX + i; z = baseZ + CELL_SIZE - 1; }
                case 2 -> { x = baseX; z = baseZ + i; }
                default -> { x = baseX + CELL_SIZE - 1; z = baseZ + i; }
            }
            if (x < 16 && z < 16) {
                for (int y = AIR_MIN_Y; y < CEILING_MIN_Y; y++) {
                    chunkData.setBlock(x, y, z, gardenPalette ? randomGardenBlock(rng) : mat);
                }
            }
        }
    }

    @Override
    public int getSpawnY() {
        return AIR_MIN_Y;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, AIR_MIN_Y, 8.5);
    }
}
