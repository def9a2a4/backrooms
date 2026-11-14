package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms.generator.build;

/**
 * Keyed randomness, as IronSource's grammar seeds it: every part of a building gets its own key,
 * mixed from its parent's and its own name or slot, and draws only from that. What one part rolls
 * never depends on how much its siblings rolled before it, so a change to one rule leaves the
 * rest of the building as it was, and any part can be rebuilt on its own.
 */
public final class Rng {

    private final long key;
    private long state;

    public Rng(long key) {
        this.key = mix(key);
        this.state = this.key;
    }

    /** The part called {@code name} under this one. */
    public Rng sub(String name) {
        return new Rng(key ^ mix(name.hashCode() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L));
    }

    /** Slot {@code i} under this one (a storey, a room, a window bay ...). */
    public Rng sub(int i) {
        return new Rng(key ^ mix((i + 1) * 0xC2B2AE3D27D4EB4FL));
    }

    public Rng sub(String name, int i) {
        return sub(name).sub(i);
    }

    public long nextLong() {
        state += 0x9E3779B97F4A7C15L;
        return mix(state);
    }

    public double next() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [lo, hi], both ends included. */
    public int range(int lo, int hi) {
        if (hi <= lo) return lo;
        return lo + (int) Math.floorMod(nextLong(), (long) (hi - lo + 1));
    }

    public double range(double lo, double hi) {
        return lo + (hi - lo) * next();
    }

    public boolean chance(double p) {
        return next() < p;
    }

    /** An index picked by weight. */
    public int pick(double... weights) {
        double sum = 0;
        for (double w : weights) sum += Math.max(0, w);
        double r = next() * sum;
        for (int i = 0; i < weights.length; i++) {
            r -= Math.max(0, weights[i]);
            if (r < 0) return i;
        }
        return weights.length - 1;
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
