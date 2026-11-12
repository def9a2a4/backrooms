package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.skin;

import java.util.List;

/**
 * The player model as cuboids in skin-pixel units, and which skin pixel shows
 * on each face of each cuboid. Model space: x runs toward the player's left
 * (their right arm sits at low x), y up, and the face/chest point toward +z.
 */
public final class SkinModel {

    public enum Face { FRONT, BACK, RIGHT, LEFT, TOP, BOTTOM }

    /**
     * A cuboid at (x, y, z) of w×h×d pixels, textured from the standard box
     * unwrap at (u, v); (ou, ov) is the overlay layer (hat, jacket, sleeves).
     */
    public record Box(int x, int y, int z, int w, int h, int d, int u, int v, int ou, int ov) {}

    private SkinModel() {}

    /** A lone head, 8×8×8. */
    public static List<Box> head() {
        return List.of(new Box(0, 0, 0, 8, 8, 8, 0, 0, 32, 0));
    }

    /** The whole player standing, in a 16 × 32 × 16 pixel space centered on z = 8. */
    public static List<Box> body(boolean slim) {
        int arm = slim ? 3 : 4;
        return List.of(
                new Box(4, 0, 6, 4, 12, 4, 0, 16, 0, 32),             // right leg
                new Box(8, 0, 6, 4, 12, 4, 16, 48, 0, 48),            // left leg
                new Box(4, 12, 6, 8, 12, 4, 16, 16, 16, 32),          // body
                new Box(4 - arm, 12, 6, arm, 12, 4, 40, 16, 40, 32),  // right arm
                new Box(12, 12, 6, arm, 12, 4, 32, 48, 48, 48),       // left arm
                new Box(4, 24, 4, 8, 8, 8, 0, 0, 32, 0)               // head
        );
    }

    /**
     * ARGB shown on {@code face} of {@code box} at model pixel (px, py, pz):
     * the overlay where it is opaque, otherwise the base layer (which may be
     * transparent — callers decide what an empty pixel becomes).
     */
    public static int color(Skin skin, Box b, Face face, int px, int py, int pz) {
        int row = b.h() - 1 - (py - b.y());
        int du, dv;
        switch (face) {
            case FRONT -> { du = b.d() + (px - b.x()); dv = b.d() + row; }
            case BACK -> { du = 2 * b.d() + b.w() + (b.x() + b.w() - 1 - px); dv = b.d() + row; }
            case RIGHT -> { du = pz - b.z(); dv = b.d() + row; }
            case LEFT -> { du = b.d() + b.w() + (b.z() + b.d() - 1 - pz); dv = b.d() + row; }
            case TOP -> { du = b.d() + (px - b.x()); dv = pz - b.z(); }
            default -> { du = b.d() + b.w() + (px - b.x()); dv = pz - b.z(); }
        }
        int over = skin.pixel(b.ou() + du, b.ov() + dv);
        if (Skin.opaque(over)) return over;
        return skin.pixel(b.u() + du, b.v() + dv);
    }
}
