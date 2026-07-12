package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

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

    protected void download(String urlStr, File destFile) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode);
        }

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    protected void startProcessAsync(ProcessBuilder pb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    Process process = null;
                    try {
                        process = pb.start();

                        final Process finalProcess = process;
                        Thread stdoutThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(finalProcess.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        Log.info("[worker] " + line);
                                    }
                                } catch (IOException e) { }
                            }
                        }, "worker-1");

                        Thread stderrThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(finalProcess.getErrorStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        Log.info("[worker] " + line);
                                    }
                                } catch (IOException e) { }
                            }
                        }, "worker-2");

                        stdoutThread.setDaemon(true);
                        stderrThread.setDaemon(true);
                        stdoutThread.start();
                        stderrThread.start();

                        int exitCode = process.waitFor();
                        Log.info("[system] Worker process restarted");
                        Thread.sleep(3000);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (process != null && process.isAlive()) process.destroyForcibly();
                        break;
                    } catch (Exception e) {
                        try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        }, "worker-main").start();
    }
}
