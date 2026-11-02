package name.d3420b8b7fe04.def9a2a4.plugintemplate.backrooms;

import java.util.logging.Logger;

/**
 * Opt-in verbose logging. Off by default; turned on with {@code debug: true} in config.yml or live with
 * {@code /backrooms debug on}. Lines log at INFO with a {@code [debug]} prefix, so they land in
 * {@code logs/latest.log} where a server owner can share them — java.util.logging's FINE never reaches
 * that file on a stock Paper server.
 */
public final class DebugLog {
    private static volatile boolean enabled;

    private DebugLog() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void log(Logger logger, String message) {
        if (enabled) logger.info("[debug] " + message);
    }
}
