package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Houses no other city has: Level 101's grammar houses ({@link AlphaHouse}), a dozen or so drawn
 * for each Level 143 city from its seed, restyled into the city's theme and handed to
 * {@link MaceCity} as ordinary city buildings alongside the pack's.
 *
 * <p>AlphaHouse builds from a handful of old blocks (oak, cobblestone, brick, glass), so a house
 * is restyled by renaming them: oak to one of the theme's woods, cobblestone and brick to one of
 * its stones. Its frame becomes a building's: the ground layer (y = -1, which the ground floor
 * replaces) is Mace's y63, everything left unset there is the city's ground, and a path runs
 * from the front door to the street. Like every building in a lot, it's padded to a square, since
 * the city turns it to face its street.
 */
final class CityHouses {

    private static final String GROUND = "minecraft:redstone_ore", PATH = "minecraft:gold_ore", AIR = "minecraft:air";
    private static final long SALT = 0xA1F4A5EL;

    /** One theme's materials: woods, and stone sets of {block, stairs, slab, wall}. */
    private record Style(String[] woods, String[][] stones, String[][] bricks, String glass, String pane) {}

    private static final String[] COBBLE = {"cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall"};
    private static final String[] BRICK = {"bricks", "brick_stairs", "brick_slab", "brick_wall"};

    private static final Map<MaceCity.Theme, Style> STYLES = Map.of(
        MaceCity.Theme.TIMBER, new Style(new String[]{"spruce", "dark_oak", "oak"},
                new String[][]{COBBLE, {"stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"},
                        {"mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall"}},
                new String[][]{BRICK}, "glass", "glass_pane"),
        MaceCity.Theme.OCHRE, new Style(new String[]{"acacia", "jungle", "oak"},
                new String[][]{{"smooth_sandstone", "smooth_sandstone_stairs", "smooth_sandstone_slab", "sandstone_wall"},
                        {"mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall"}},
                new String[][]{BRICK, {"granite", "granite_stairs", "granite_slab", "granite_wall"}}, "glass", "glass_pane"),
        MaceCity.Theme.ASHEN, new Style(new String[]{"dark_oak", "spruce", "pale_oak"},
                new String[][]{{"cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall"},
                        {"deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall"},
                        {"polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
                                "polished_blackstone_brick_wall"}},
                new String[][]{{"deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall"},
                        {"tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall"}},
                "gray_stained_glass", "gray_stained_glass_pane"));

    private CityHouses() {}

    /** How many of its own houses a city of this theme gets: none for Mace's themes. */
    static int count(MaceCity.Theme theme) {
        return STYLES.containsKey(theme) ? 18 : 0;
    }

    /** The city's own houses, from its seed: the same city always gets the same ones. */
    static List<MaceBuilding> make(MaceCity.Plan plan) {
        int n = count(plan.theme());
        List<MaceBuilding> out = new ArrayList<>(n);
        if (n == 0) return out;
        Style st = STYLES.get(plan.theme());
        Random rng = new Random(plan.seed() ^ SALT);
        for (int i = 0; i < n; i++) {
            long seed = rng.nextLong();
            Map<String, String> names = restyle(st, rng);
            out.add(house(AlphaHouse.build(seed), names, plan.theme(), i, "very common", rng.nextInt(2) == 0));
        }
        return out;
    }

    /** The block renames for one house: oak to a wood, cobblestone and brick to stones. */
    private static Map<String, String> restyle(Style st, Random rng) {
        Map<String, String> m = new HashMap<>();
        String wood = st.woods()[rng.nextInt(st.woods().length)];
        for (String part : new String[]{"planks", "log", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor",
                "pressure_plate", "button", "sign", "wall_sign"}) {
            m.put("oak_" + part, wood + "_" + part);
        }
        if (wood.equals("pale_oak")) m.put("oak_log", "stripped_pale_oak_log");
        String[] stone = st.stones()[rng.nextInt(st.stones().length)];
        String[] brick = st.bricks()[rng.nextInt(st.bricks().length)];
        for (int k = 0; k < 4; k++) {
            m.put(COBBLE[k], stone[k]);
            m.put(BRICK[k], brick[k]);
        }
        m.put("glass", st.glass());
        m.put("glass_pane", st.pane());
        m.put("cave_air", "air");
        return m;
    }

    private static String rename(String state, Map<String, String> names) {
        int b = state.indexOf('[');
        String name = state.substring(state.indexOf(':') + 1, b < 0 ? state.length() : b);
        String to = names.get(name);
        return to == null ? state : "minecraft:" + to + (b < 0 ? "" : state.substring(b));
    }

    /** A grid as a city building: the ground layer, a path to the street, a square footprint. */
    private static MaceBuilding house(Grid g, Map<String, String> names, MaceCity.Theme theme, int i, String freq,
                                      boolean sign) {
        // Crop Level 101's wide border to the house and a block of ground around it (eaves included).
        int top = g.minY, x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (int y = g.minY; y <= g.maxY(); y++) {
            for (int z = g.minZ; z <= g.maxZ(); z++) {
                for (int x = g.minX; x <= g.maxX(); x++) {
                    if (!g.solid(x, y, z)) continue;
                    top = y;
                    x0 = Math.min(x0, x);
                    x1 = Math.max(x1, x);
                    minZ = Math.min(minZ, z);
                    z1 = Math.max(z1, z);
                }
            }
        }
        x0 = Math.max(g.minX, x0 - 1);
        x1 = Math.min(g.maxX(), x1 + 1);
        minZ = Math.max(g.minZ, Math.min(minZ, -1) - 1);   // at least a step of path before the door
        z1 = Math.min(g.maxZ(), z1 + 1);
        int w = x1 - x0 + 1, l = z1 - minZ + 1, h = top - g.minY + 1;
        int side = Math.max(w, l);
        int dx = (side - w) / 2;                 // deeper than wide: ground both sides; wider: ground behind
        String[] states = new String[side * side * h];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    int gx = x - dx + x0, gy = y + g.minY, gz = z + minZ;
                    String s = x - dx >= 0 && x - dx < w && z < l ? g.get(gx, gy, gz) : null;
                    states[(y * side + z) * side + x] = s == null ? (y == 0 ? GROUND : AIR) : rename(s, names);
                }
            }
        }
        // the front door's path, from the doorstep to the street edge
        int doorX = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) {
            String s = g.get(x, 0, 0);
            if (s == null || !s.contains("_door") || !s.contains("half=lower")) continue;
            if (doorX == Integer.MIN_VALUE) doorX = x;
            for (int z = minZ; z < 0; z++) {
                int idx = (z - minZ) * side + (x - x0 + dx);
                if (states[idx].equals(GROUND)) states[idx] = PATH;
            }
        }
        Map<Integer, String[]> signs = new HashMap<>();
        if (sign && doorX != Integer.MIN_VALUE) {   // a house name beside the door, on the wall
            int sx = doorX - 1, wallIdx = ((1 - g.minY) * side + (0 - minZ)) * side + (sx - x0 + dx);
            int at = ((1 - g.minY) * side + (-1 - minZ)) * side + (sx - x0 + dx);
            String wall = states[wallIdx];
            if (at >= 0 && states[at].equals(AIR) && !wall.equals(AIR) && !wall.contains("glass") && !wall.contains("door")) {
                String wood = names.get("oak_wall_sign");
                states[at] = "minecraft:" + wood + "[facing=north,waterlogged=false]";
                signs.put(at, new String[]{"[house]", "", "", ""});
            }
        }
        Set<Integer> chests = new HashSet<>();
        for (int k = 0; k < states.length; k++) if (states[k].startsWith("minecraft:chest")) chests.add(k);
        Map<String, String> meta = Map.of("type", "city", "frequency", freq, "files", theme.file,
                "size_x", String.valueOf(side), "size_z", String.valueOf(side));
        return new MaceBuilding("Planned House " + (i + 1), "city", side, h, side, 63, meta, states, signs, chests);
    }
}
