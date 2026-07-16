package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.File;
import java.io.IOException;

public abstract class AbstractService {

    public static final File LIB_PATH = new File(System.getProperty("user.dir"), "lib");
    public static final boolean OS_IS_ARM = System.getProperty("os.arch", "").contains("arm")
            || System.getProperty("os.arch", "").contains("aarch64");

    protected final ServerConfig config;

    public AbstractService(ServerConfig config) {
        this.config = config;
    }

    public abstract String getAppDownloadUrl();
    public abstract void install() throws Exception;
    public abstract void startup() throws Exception;
    public abstract String getAppName();

    protected File initLibPath() {
        File path = new File(LIB_PATH, getAppName());
        if (!path.exists()) path.mkdirs();
        return path;
    }

    protected File getLibPath() {
        return new File(LIB_PATH, getAppName());
    }

    protected void setExecutePermission(File file) throws IOException {
        if (file.exists()) {
            if (!file.setExecutable(true, false)) {
                Log.warn("Failed to set permission for %s", file.getName());
            }
        }
    }
}
