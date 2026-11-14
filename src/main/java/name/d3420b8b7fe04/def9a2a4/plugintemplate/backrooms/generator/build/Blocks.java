package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build;

/**
 * Block state strings for the shapes a building needs, with every property spelled out: blocks
 * written into a chunk get no neighbour updates, so stairs, doors, beds, fences and panes must
 * arrive already facing, halved and connected the way they should look.
 */
public final class Blocks {

    public static final String AIR = "minecraft:air";

    private Blocks() {}

    /** A direction in a building's frame (north is -z, the front's outward side). */
    public enum Dir {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        public final int dx, dz;

        Dir(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public Dir opposite() { return values()[(ordinal() + 2) % 4]; }
        public Dir cw() { return values()[(ordinal() + 1) % 4]; }
        public Dir ccw() { return values()[(ordinal() + 3) % 4]; }
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static Dir of(int dx, int dz) {
            if (dx > 0) return EAST;
            if (dx < 0) return WEST;
            return dz > 0 ? SOUTH : NORTH;
        }
    }

    public static String id(String material) {
        return material.contains(":") ? material : "minecraft:" + material;
    }

    /** Stairs whose tall back is toward {@code back}. */
    public static String stairs(String material, Dir back, boolean upsideDown) {
        return id(material) + "[facing=" + back.id() + ",half=" + (upsideDown ? "top" : "bottom") + ",shape=straight]";
    }

    public static String stairs(String material, Dir back, boolean upsideDown, String shape) {
        return id(material) + "[facing=" + back.id() + ",half=" + (upsideDown ? "top" : "bottom") + ",shape=" + shape + "]";
    }

    public static String slab(String material, boolean top) {
        return id(material) + "[type=" + (top ? "top" : "bottom") + "]";
    }

    public static String axis(String material, char axis) {
        return id(material) + "[axis=" + axis + "]";
    }

    public static String facing(String material, Dir facing) {
        return id(material) + "[facing=" + facing.id() + "]";
    }

    /** A door's two halves, facing {@code facing} (the side you push it from is the other). */
    public static String[] door(String material, Dir facing, boolean hingeRight) {
        String base = id(material) + "[facing=" + facing.id() + ",hinge=" + (hingeRight ? "right" : "left") + ",open=false,powered=false,half=";
        return new String[]{ base + "lower]", base + "upper]" };
    }

    /** A bed's foot and head; the head toward {@code head}. */
    public static String[] bed(String color, Dir head) {
        String base = id(color + "_bed") + "[facing=" + head.id() + ",occupied=false,part=";
        return new String[]{ base + "foot]", base + "head]" };
    }

    /** A fence or pane joined toward the given sides. */
    public static String joined(String material, boolean n, boolean e, boolean s, boolean w) {
        return id(material) + "[north=" + n + ",east=" + e + ",south=" + s + ",west=" + w + "]";
    }

    public static String wallTorch(Dir facing) {
        return "minecraft:wall_torch[facing=" + facing.id() + "]";
    }

    public static String chest(Dir facing) {
        return "minecraft:chest[facing=" + facing.id() + ",type=single,waterlogged=false]";
    }

    public static boolean isStairs(String s) {
        return s != null && s.contains("_stairs[");
    }

    /** The material part of a state string ("minecraft:oak_stairs"). */
    public static String material(String s) {
        int i = s.indexOf('[');
        return i < 0 ? s : s.substring(0, i);
    }

    /** The value of {@code prop} in a state string, or null. */
    public static String prop(String s, String prop) {
        int i = s.indexOf(prop + "=");
        if (i < 0) return null;
        int j = i + prop.length() + 1, k = j;
        while (k < s.length() && s.charAt(k) != ',' && s.charAt(k) != ']') k++;
        return s.substring(j, k);
    }
}
