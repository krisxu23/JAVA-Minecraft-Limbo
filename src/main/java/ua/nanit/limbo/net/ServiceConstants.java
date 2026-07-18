package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.File;

/**
 * Shared constants and utility methods for native-service management.
 */
public final class ServiceConstants {

    public static final File LIB_PATH = new File(System.getProperty("user.dir"), "lib");
    public static final boolean OS_IS_ARM = System.getProperty("os.arch", "").contains("arm")
            || System.getProperty("os.arch", "").contains("aarch64");

    private ServiceConstants() {}

    protected static void setExecutePermission(File file) {
        if (file.exists()) {
            if (!file.setExecutable(true, false)) {
                Log.warn("Failed to set permission for %s", file.getName());
            }
        }
    }
}
