package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build;

import name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build.Blocks.Dir;

/**
 * Pitched roofs on the block grid. As in IronSource's houses, a roof rises in whole ratios so its
 * ridge and eaves land on blocks: 1:1 in stairs, 1:2 in slabs (a half block a block). Every
 * column of the roof's rectangle gets its height from its distance to the nearest eave; where
 * two slopes meet at a right angle (a hip) the stairs take their corner shapes the way the game
 * would give them, and where two slopes meet head on (a ridge) a slab caps them.
 *
 * <p>The ends that are not eaves (a gable's, a lean-to's sides and high wall) are walled up to
 * the roof. A side shared with another part of the building is neither eave nor end: the roof
 * stops at that wall, which the other part builds.
 */
public final class Roof {

    public enum Kind { GABLE, HIP, SHED }

    private Roof() {}

    /**
     * A roof over the walls x0..x1, z0..z1 (outer faces), its first course at {@code baseY} (the
     * layer above the walls' top). {@code alongX}: a gable's ridge runs along x (eaves north and
     * south), else along z. {@code low}: a lean-to's low side. {@code shared}: the sides (bits
     * by Dir ordinal) that abut another part. Returns the roof's highest y.
     */
    public static int build(Grid g, int x0, int z0, int x1, int z1, int baseY, Kind kind, boolean alongX,
                            Dir low, int shared, boolean halfSlope,
                            String stairMat, String slabMat, String endMat) {
        boolean[] eave = new boolean[4];
        switch (kind) {
            case GABLE -> {
                if (alongX) { eave[Dir.NORTH.ordinal()] = true; eave[Dir.SOUTH.ordinal()] = true; }
                else { eave[Dir.EAST.ordinal()] = true; eave[Dir.WEST.ordinal()] = true; }
            }
            case HIP -> { for (int i = 0; i < 4; i++) eave[i] = true; }
            case SHED -> eave[low.ordinal()] = true;
        }
        for (int i = 0; i < 4; i++) if ((shared & (1 << i)) != 0) eave[i] = false;
        if (!eave[0] && !eave[1] && !eave[2] && !eave[3]) return baseY - 1;

        // The roof's rectangle: a block out past every wall that is not shared.
        int rx0 = x0 - ((shared & bit(Dir.WEST)) != 0 ? 0 : 1);
        int rx1 = x1 + ((shared & bit(Dir.EAST)) != 0 ? 0 : 1);
        int rz0 = z0 - ((shared & bit(Dir.NORTH)) != 0 ? 0 : 1);
        int rz1 = z1 + ((shared & bit(Dir.SOUTH)) != 0 ? 0 : 1);
        // A shared side's roof starts inside that wall.
        if ((shared & bit(Dir.WEST)) != 0) rx0 = x0 + 1;
        if ((shared & bit(Dir.EAST)) != 0) rx1 = x1 - 1;
        if ((shared & bit(Dir.NORTH)) != 0) rz0 = z0 + 1;
        if ((shared & bit(Dir.SOUTH)) != 0) rz1 = z1 - 1;

        int top = baseY;
        for (int x = rx0; x <= rx1; x++) {
            for (int z = rz0; z <= rz1; z++) {
                int[] dist = { z - rz0, rx1 - x, rz1 - z, x - rx0 };   // to N, E, S, W edges
                int d = Integer.MAX_VALUE;
                for (int i = 0; i < 4; i++) if (eave[i]) d = Math.min(d, dist[i]);
                int first = -1;
                boolean opposite = false;   // two slopes meeting head on: the ridge
                for (int i = 0; i < 4; i++) {
                    if (!eave[i] || dist[i] != d) continue;
                    if (first < 0) first = i;
                    else if ((first + 2) % 4 == i) opposite = true;
                }
                Dir up = Dir.values()[first].opposite();
                int y;
                String block;
                if (halfSlope) {
                    int h = 2 * (baseY - 1) + 1 + d;      // in half blocks
                    y = Math.floorDiv(h, 2);
                    block = Blocks.slab(slabMat, Math.floorMod(h, 2) == 1);
                } else {
                    y = baseY - 1 + d;
                    block = opposite ? Blocks.slab(slabMat, false) : Blocks.stairs(stairMat, up, false);
                }
                g.add(x, y, z, block);
                top = Math.max(top, y);
                // Wall up the ends (on the wall's own line) to meet the roof.
                boolean onEnd = false;
                for (int i = 0; i < 4; i++) {
                    if (eave[i] || (shared & (1 << i)) != 0) continue;
                    int line = switch (i) { case 0 -> z0; case 1 -> x1; case 2 -> z1; default -> x0; };
                    int c = (i % 2 == 0) ? z : x;
                    if (c == line && x >= x0 && x <= x1 && z >= z0 && z <= z1) onEnd = true;
                }
                if (onEnd) for (int yy = baseY; yy < y; yy++) g.add(x, yy, z, Blocks.id(endMat));
            }
        }
        if (!halfSlope) shapeStairs(g, rx0, baseY - 1, rz0, rx1, top, rz1);
        return top;
    }

    private static int bit(Dir d) {
        return 1 << d.ordinal();
    }

    /** Gives every stair in the box the shape the game would (StairBlock.getStairsShape). */
    public static void shapeStairs(Grid g, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    String s = g.get(x, y, z);
                    if (!Blocks.isStairs(s)) continue;
                    Dir f = Dir.valueOf(Blocks.prop(s, "facing").toUpperCase(java.util.Locale.ROOT));
                    String half = Blocks.prop(s, "half");
                    String shape = "straight";
                    String behind = g.get(x + f.dx, y, z + f.dz);
                    if (sameHalf(behind, half)) {
                        Dir bf = facing(behind);
                        if (axis(bf) != axis(f) && canTakeShape(g, x, y, z, f, half, bf.opposite())) {
                            shape = bf == f.ccw() ? "outer_left" : "outer_right";
                        }
                    }
                    if (shape.equals("straight")) {
                        Dir o = f.opposite();
                        String front = g.get(x + o.dx, y, z + o.dz);
                        if (sameHalf(front, half)) {
                            Dir ff = facing(front);
                            if (axis(ff) != axis(f) && canTakeShape(g, x, y, z, f, half, ff)) {
                                shape = ff == f.ccw() ? "inner_left" : "inner_right";
                            }
                        }
                    }
                    if (!shape.equals("straight")) {
                        g.set(x, y, z, Blocks.stairs(Blocks.material(s), f, half.equals("top"), shape));
                    }
                }
            }
        }
    }

    private static boolean sameHalf(String s, String half) {
        return Blocks.isStairs(s) && half.equals(Blocks.prop(s, "half"));
    }

    private static Dir facing(String s) {
        return Dir.valueOf(Blocks.prop(s, "facing").toUpperCase(java.util.Locale.ROOT));
    }

    private static int axis(Dir d) {
        return d.dx != 0 ? 0 : 1;
    }

    private static boolean canTakeShape(Grid g, int x, int y, int z, Dir f, String half, Dir dir) {
        String n = g.get(x + dir.dx, y, z + dir.dz);
        return !Blocks.isStairs(n) || facing(n) != f || !half.equals(Blocks.prop(n, "half"));
    }
}
