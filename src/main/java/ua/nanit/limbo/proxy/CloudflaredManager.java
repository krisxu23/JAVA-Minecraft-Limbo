package ua.nanit.limbo.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ua.nanit.limbo.server.Log;

/**
 * Manages cloudflared (Argo Tunnel) via JNA native .so loading (eooce/sbx-native).
 *
 * Downloads bot.so from https://{arch}.31888.xyz, loads via JNA,
 * calls StartCloudflared() / StopCloudflared() — no child processes.
 *
 * For quick tunnels, monitors boot.log to discover the trycloudflare.com domain.
 */
public final class CloudflaredManager {

    private static NativeLibrary cfLib;
    private static Function stopCloudflared;
    private static boolean running = false;

    private static final Gson GSON = new Gson();
    private static final Pattern QUICK_TUNNEL_PATTERN = Pattern.compile("https://([A-Za-z0-9.-]+\\.trycloudflare\\.com)");

    private CloudflaredManager() {}

    // ==================== JNA Native Service ====================

    /**
     * Downloads bot.so (if not cached), loads via JNA, and starts cloudflared
     * in a background daemon thread.
     */
    public static void start(Map<String, String> env) throws Exception {
        String arch = detectArch();
        String baseUrl = "https://" + arch + ".31888.xyz";
        Path libPath = downloadLibrary(baseUrl + "/bot.so", "bot.so", env);

        // Handle TunnelSecret auth (write config files before starting)
        String argoAuth = env.getOrDefault("ARGO_AUTH", "");
        String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
        String filePath = env.getOrDefault("FILE_PATH", "world");
        Path runtimeDir = Paths.get(filePath).normalize();

        if (!argoAuth.isEmpty() && !argoDomain.isEmpty() && argoAuth.contains("TunnelSecret")) {
            Log.info("[CF] Writing TunnelSecret config files...");
            Files.createDirectories(runtimeDir);
            Files.write(runtimeDir.resolve("tunnel.json"), argoAuth.getBytes(StandardCharsets.UTF_8));
            String tunnelId = extractTunnelId(argoAuth);
            String yaml = "tunnel: " + tunnelId + "\n" +
                    "credentials-file: " + runtimeDir.resolve("tunnel.json").toString().replace("\\", "/") + "\n" +
                    "protocol: http2\n\n" +
                    "ingress:\n" +
                    "  - hostname: " + argoDomain + "\n" +
                    "    service: http://localhost:" + env.getOrDefault("ARGO_PORT", "8001") + "\n" +
                    "    originRequest:\n" +
                    "      noTLSVerify: true\n" +
                    "  - service: http_status:404\n";
            Files.write(runtimeDir.resolve("tunnel.yml"), yaml.getBytes(StandardCharsets.UTF_8));
            Log.info("[CF] TunnelSecret config files written");
        }

        Log.info("[CF] Loading native library: " + libPath);
        cfLib = NativeLibrary.getInstance(libPath.toString());
        Function startFn = cfLib.getFunction("StartCloudflared");
        stopCloudflared = cfLib.getFunction("StopCloudflared");

        String payload = buildPayload(env, runtimeDir);
        if (payload == null) {
            Log.info("[CF] Argo tunnel disabled, not starting cloudflared");
            return;
        }

        Log.info("[CF] Starting cloudflared native...");
        Thread t = new Thread(() -> {
            try {
                int code = startFn.invokeInt(new Object[]{payload});
                Log.info("[CF] cloudflared native exited with code " + code);
            } catch (Exception e) {
                Log.warn("[CF] cloudflared native error: " + e.getMessage());
            }
        }, "cf-native");
        t.setDaemon(true);
        t.start();
        running = true;
        Log.info("[CF] cloudflared native started");

        // Wait for quick tunnel domain and regenerate subscription
        if (argoAuth.isEmpty() || argoDomain.isEmpty()) {
            discoverQuickTunnelDomain(env, runtimeDir);
        } else {
            Log.info("[CF] Using fixed tunnel domain: " + argoDomain);
            SubscriptionGenerator.generate(env);
        }
    }

    /**
     * Stops cloudflared by calling StopCloudflared() via JNA.
     */
    public static void shutdown() {
        if (running && stopCloudflared != null) {
            try {
                int code = stopCloudflared.invokeInt(new Object[]{});
                running = false;
                Log.info("[CF] cloudflared stopped with code " + code);
            } catch (Exception e) {
                Log.warn("[CF] Error stopping cloudflared: " + e.getMessage());
            }
        }
    }

    // ==================== Payload Builder ====================

    /**
     * Builds the JSON payload for StartCloudflared based on env vars.
     * Returns null if Argo is disabled.
     */
    private static String buildPayload(Map<String, String> env, Path runtimeDir) {
        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if ("true".equalsIgnoreCase(disableArgo)) {
            return null;
        }

        String argoAuth = env.getOrDefault("ARGO_AUTH", "");
        String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
        String argoPort = env.getOrDefault("ARGO_PORT", "8001");

        JsonArray args = new JsonArray();

        if (!argoAuth.isEmpty() && !argoDomain.isEmpty()) {
            // Check if it's a token (alphanumeric + =, 120-250 chars)
            if (argoAuth.matches("^[A-Za-z0-9=]{120,250}$")) {
                args.add("tunnel");
                args.add("--edge-ip-version");
                args.add("auto");
                args.add("--no-autoupdate");
                args.add("--protocol");
                args.add("http2");
                args.add("run");
                args.add("--token");
                args.add(argoAuth);
            } else if (argoAuth.contains("TunnelSecret")) {
                args.add("tunnel");
                args.add("--edge-ip-version");
                args.add("auto");
                args.add("--config");
                args.add(runtimeDir.resolve("tunnel.yml").toString().replace("\\", "/"));
                args.add("run");
            }
        }

        if (args.size() == 0) {
            // Quick tunnel
            Path bootLog = runtimeDir.resolve("boot.log");
            args.add("tunnel");
            args.add("--edge-ip-version");
            args.add("auto");
            args.add("--no-autoupdate");
            args.add("--protocol");
            args.add("http2");
            args.add("--logfile");
            args.add(bootLog.toString().replace("\\", "/"));
            args.add("--loglevel");
            args.add("info");
            args.add("--url");
            args.add("http://localhost:" + argoPort);
        }

        JsonObject payload = new JsonObject();
        payload.add("args", args);
        return GSON.toJson(payload);
    }

    // ==================== Quick Tunnel Domain Discovery ====================

    /**
     * Waits for the quick tunnel domain to appear in boot.log, then
     * regenerates the subscription.
     */
    private static void discoverQuickTunnelDomain(Map<String, String> env, Path runtimeDir) {
        Path bootLog = runtimeDir.resolve("boot.log");
        Log.info("[CF] Waiting for quick tunnel domain in boot.log...");

        String domain = waitForDomain(bootLog, 60_000);
        if (domain == null) {
            Log.warn("[CF] Quick tunnel domain not found, retrying...");
            try { Files.deleteIfExists(bootLog); } catch (IOException ignored) {}
            sleep(5000);
            domain = waitForDomain(bootLog, 60_000);
        }

        if (domain != null) {
            Log.info("[CF] Quick tunnel domain: " + domain);
            env.put("ARGO_DOMAIN", domain);
            try {
                SubscriptionGenerator.generate(env);
                Log.info("[CF] Subscription regenerated with Argo domain");
            } catch (Exception e) {
                Log.warn("[CF] Failed to regenerate subscription: " + e.getMessage());
            }
        } else {
            Log.warn("[CF] Could not discover quick tunnel domain");
        }
    }

    /**
     * Polls boot.log for a trycloudflare.com URL up to the given timeout (ms).
     */
    private static String waitForDomain(Path bootLog, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastContent = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                if (bootLog.toFile().exists()) {
                    byte[] raw = Files.readAllBytes(bootLog);
                    String content = new String(raw, StandardCharsets.UTF_8);
                    if (!content.equals(lastContent)) {
                        lastContent = content;
                        Matcher m = QUICK_TUNNEL_PATTERN.matcher(content);
                        String found = null;
                        while (m.find()) found = m.group(1);
                        if (found != null) return found;
                    }
                }
            } catch (IOException ignored) {}
            sleep(1000);
        }
        return null;
    }

    private static String extractTunnelId(String auth) {
        Matcher m = Pattern.compile("\"TunnelID\"\\s*:\\s*\"([^\"]+)\"").matcher(auth);
        if (m.find()) return m.group(1);
        m = Pattern.compile("TunnelID[^:]*:\\s*\"([^\"]+)\"").matcher(auth);
        return m.find() ? m.group(1) : "";
    }

    // ==================== .so Library Download ====================

    private static Path downloadLibrary(String url, String fileName, Map<String, String> env) throws IOException {
        String filePath = env.getOrDefault("FILE_PATH", "world");
        Path dir = Paths.get(filePath).normalize();
        Path target = dir.resolve(fileName);

        if (target.toFile().exists()) {
            Log.info("[CF] Using cached native library: " + target);
            return target;
        }

        Files.createDirectories(dir);
        Path tmp = dir.resolve(fileName + ".download");

        Log.info("[CF] Downloading " + url + " -> " + target);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(180000);
            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode + " for " + url);
            }
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) == 0) {
                Files.deleteIfExists(tmp);
                throw new IOException("Downloaded library is empty: " + url);
            }
            String expectedSha = env.getOrDefault("BOT_LIB_SHA256", "").trim();
            if (!expectedSha.isEmpty()) {
                String actual = sha256Hex(tmp);
                if (!expectedSha.equalsIgnoreCase(actual)) {
                    Files.deleteIfExists(tmp);
                    throw new IOException("bot.so SHA-256 mismatch (expected " + expectedSha + ", got " + actual + ")");
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            if (!target.toFile().setExecutable(true, false)) {
                Log.warn("[CF] Failed to set executable on " + target);
            }
            Log.info("[CF] Downloaded " + fileName + " (" + target.toFile().length() + " bytes)");
        } finally {
            conn.disconnect();
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
        return target;
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            return "amd64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        } else {
            throw new RuntimeException("Unsupported architecture: " + arch);
        }
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
