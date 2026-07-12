package ua.nanit.limbo.proxy;

import ua.nanit.limbo.server.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class AbstractProxyService {

    public static final File BINARY_PATH = new File(System.getProperty("user.dir"), "bin");
    public static final boolean OS_IS_ARM = System.getProperty("os.arch", "").contains("arm")
            || System.getProperty("os.arch", "").contains("aarch64");

    protected final ProxyConfig config;

    public AbstractProxyService(ProxyConfig config) {
        this.config = config;
    }

    public abstract String getAppDownloadUrl();

    public abstract void install() throws Exception;

    public abstract void startup() throws Exception;

    public abstract String getAppName();

    protected File initBinaryPath() {
        File appBinaryPath = new File(BINARY_PATH, getAppName());
        if (!appBinaryPath.exists()) {
            appBinaryPath.mkdirs();
        }
        return appBinaryPath;
    }

    protected File getBinaryPath() {
        return new File(BINARY_PATH, getAppName());
    }

    protected void setExecutePermission(File file) throws IOException {
        if (file.exists()) {
            if (!file.setExecutable(true, false)) {
                Log.warn("Failed to set executable permission for %s", file.getName());
            }
        }
    }

    protected void download(String urlStr, File destFile) throws IOException {
        Log.info("Downloading %s from %s...", destFile.getName(), urlStr);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + responseCode + " for " + urlStr);
        }

        long totalBytes = conn.getContentLengthLong();
        long downloadedBytes = 0;
        long lastProgressTime = 0;

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                downloadedBytes += len;

                long now = System.currentTimeMillis();
                if (totalBytes > 0 && now - lastProgressTime > 3000) {
                    int percent = (int) (downloadedBytes * 100 / totalBytes);
                    Log.info("Download progress: %d%% (%d/%d bytes)", percent, downloadedBytes, totalBytes);
                    lastProgressTime = now;
                }
            }
        }

        Log.info("Download completed: %s", destFile.getPath());
    }

    protected void startProcessAsync(ProcessBuilder pb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    Process process = null;
                    try {
                        Log.info("Starting %s...", getAppName());
                        process = pb.start();

                        final Process finalProcess = process;
                        Thread stdoutThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(finalProcess.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        Log.info("[%s] %s", getAppName(), line);
                                    }
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }, getAppName() + "-stdout");

                        Thread stderrThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try (BufferedReader reader = new BufferedReader(
                                        new InputStreamReader(finalProcess.getErrorStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        Log.error("[%s] %s", getAppName(), line);
                                    }
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        }, getAppName() + "-stderr");

                        stdoutThread.setDaemon(true);
                        stderrThread.setDaemon(true);
                        stdoutThread.start();
                        stderrThread.start();

                        int exitCode = process.waitFor();
                        Log.warn("%s exited with code %d, restarting in 3 seconds...", getAppName(), exitCode);

                        Thread.sleep(3000);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (process != null && process.isAlive()) {
                            process.destroyForcibly();
                        }
                        Log.info("%s stopped", getAppName());
                        break;
                    } catch (Exception e) {
                        Log.error("Error running %s: %s", getAppName(), e.getMessage());
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, getAppName() + "-manager").start();
    }
}
