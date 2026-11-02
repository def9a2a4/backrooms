package name.d3420b8b7fe04.def9a2a4.plugintemplate;

import io.papermc.paper.ServerBuildInfo;

/**
 * Minimum supported Minecraft version. The bundled datapack uses the 1.21.11+
 * dimension_type/biome schema (older servers refuse to boot with it), and the
 * code uses org.bukkit.GameRules (1.21.11+). Older Paper builds don't reliably
 * enforce paper-plugin.yml's api-version, so check explicitly.
 */
public final class ServerVersionCheck {

    public static final String MIN_VERSION = "1.21.11";

    private ServerVersionCheck() {}

    public static String serverVersion() {
        return ServerBuildInfo.buildInfo().minecraftVersionId();
    }

    public static boolean isSupported() {
        return compare(serverVersion(), MIN_VERSION) >= 0;
    }

    /** Numeric dotted-version compare; non-numeric suffixes (e.g. "-rc1") are ignored. */
    static int compare(String a, String b) {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? parse(pa[i]) : 0;
            int y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
