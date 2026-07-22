package ua.nanit.limbo.proxy;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import ua.nanit.limbo.server.Log;

/**
 * Manages sing-box proxy: downloads the binary, generates Reality keys,
 * creates TLS certificates, builds the sing-box configuration JSON,
 * and runs the process with a self-healing watchdog.
 *
 * The watchdog restarts sing-box on non-zero exit (3s delay) and
 * stops on clean exit (0) or JVM shutdown.
 */
public final class SingBoxManager {

    private static volatile Process currentProcess;

    private SingBoxManager() {}

    private static final Gson GSON = new Gson();

    // ==================== Self-Healing Watchdog ====================

    /**
     * Starts sing-box with a self-healing loop. Blocks until the process
     * exits cleanly (exit=0) or the current thread is interrupted.
     * Intended to run on a dedicated daemon thread.
     */
    public static void runWithSelfHealing(Map<String, String> env) {
        while (true) {
            try {
                Path configPath = generateConfig(env);
                currentProcess = startProcess(configPath);
                int exitCode = currentProcess.waitFor();
                if (exitCode == 0) {
                    Log.info("[SBX] sing-box exited cleanly (exit=0), stopping watchdog");
                    break;
                }
                Log.warn("[SBX] sing-box died (exit=" + exitCode + "), restarting in 3s...");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.warn("[SBX] Watchdog interrupted, stopping");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.warn("[SBX] Error: " + e.getMessage() + ", restarting in 10s...");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private static Process startProcess(Path configPath) throws Exception {
        Log.info("[SBX] Starting sing-box...");
        ProcessBuilder pb = new ProcessBuilder(
                getBinaryPath().toString(),
                "run", "-c", configPath.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }

    /**
     * Terminates the current sing-box process (if alive).
     * Called from the JVM shutdown hook.
     */
    public static void shutdown() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            Log.info("[SBX] Terminating sing-box...");
            p.destroy();
        }
    }

    // ==================== Sing-Box Config Generation ====================

    /**
     * Generates a sing-box JSON configuration file and returns its path.
     */
    public static Path generateConfig(Map<String, String> env) throws Exception {
        String uuid = env.getOrDefault("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        String hy2Port = env.getOrDefault("HY2_PORT", "");
        String tuicPort = env.getOrDefault("TUIC_PORT", "");
        String realityPort = env.getOrDefault("REALITY_PORT", "");
        String s5Port = env.getOrDefault("S5_PORT", "");
        String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
        String argoPort = env.getOrDefault("ARGO_PORT", "8001");
        String cfIp = env.getOrDefault("CFIP", "cdns.doon.eu.org");
        String realityPrivateKey = env.getOrDefault("REALITY_PRIVATE_KEY", "");
        String realityShortId = env.getOrDefault("REALITY_SHORT_ID", "");

        String serverAddr = argoDomain.isEmpty() ? cfIp : argoDomain;

        // Generate self-signed cert for TLS protocols
        Path certDir = Paths.get(System.getProperty("java.io.tmpdir"), "certs");
        Files.createDirectories(certDir);
        Path certFile = certDir.resolve("cert.pem");
        Path keyFile = certDir.resolve("key.pem");

        if (!certFile.toFile().exists()) {
            Log.info("[SBX] Generating self-signed certificate...");
            try {
                ProcessBuilder pb1 = new ProcessBuilder("openssl", "ecparam", "-genkey", "-name", "prime256v1",
                    "-out", keyFile.toAbsolutePath().toString());
                pb1.redirectErrorStream(true);
                Process keyGen = pb1.start();
                int keyExit = keyGen.waitFor();
                if (keyExit != 0) {
                    throw new IOException("openssl ecparam exited with code " + keyExit);
                }

                ProcessBuilder pb2 = new ProcessBuilder("openssl", "req", "-new", "-x509", "-days", "3650",
                    "-key", keyFile.toAbsolutePath().toString(),
                    "-out", certFile.toAbsolutePath().toString(),
                    "-subj", "/CN=bing.com");
                pb2.redirectErrorStream(true);
                Process certGen = pb2.start();
                int certExit = certGen.waitFor();
                if (certExit != 0) {
                    throw new IOException("openssl req exited with code " + certExit);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while generating certificate");
            }
        }

        String certPath = certFile.toAbsolutePath().toString().replace("\\", "/");
        String keyPath = keyFile.toAbsolutePath().toString().replace("\\", "/");

        JsonArray inbounds = new JsonArray();

        // VMess WS inbound
        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if (!"true".equalsIgnoreCase(disableArgo)) {
            JsonObject transport = new JsonObject();
            transport.addProperty("type", "ws");
            transport.addProperty("path", "/vmess-argo");
            transport.addProperty("max_early_data", 2560);
            transport.addProperty("early_data_header_name", "Sec-WebSocket-Protocol");

            JsonObject user = new JsonObject();
            user.addProperty("uuid", uuid);
            user.addProperty("alterId", 0);
            JsonArray users = new JsonArray();
            users.add(user);

            JsonObject vmessInbound = new JsonObject();
            vmessInbound.addProperty("type", "vmess");
            vmessInbound.addProperty("tag", "vmess-ws");
            vmessInbound.addProperty("listen", "0.0.0.0");
            vmessInbound.addProperty("listen_port", Integer.parseInt(argoPort));
            vmessInbound.add("users", users);
            vmessInbound.add("transport", transport);
            inbounds.add(vmessInbound);
        }

        // SOCKS5 inbound
        if (!s5Port.isEmpty()) {
            for (String port : s5Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("username", uuid.substring(0, 8));
                    user.addProperty("password", uuid.substring(uuid.length() - 12));
                    JsonArray users = new JsonArray();
                    users.add(user);

                    JsonObject s5 = new JsonObject();
                    s5.addProperty("type", "socks");
                    s5.addProperty("tag", "socks5-in");
                    s5.addProperty("listen", "0.0.0.0");
                    s5.addProperty("listen_port", Integer.parseInt(port));
                    s5.add("users", users);
                    inbounds.add(s5);
                }
            }
        }

        // Hysteria2 inbound
        if (!hy2Port.isEmpty()) {
            for (String port : hy2Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("password", uuid);
                    JsonArray users = new JsonArray();
                    users.add(user);

                    JsonArray alpn = new JsonArray();
                    alpn.add("h3");

                    JsonObject tls = new JsonObject();
                    tls.addProperty("enabled", true);
                    tls.add("alpn", alpn);
                    tls.addProperty("min_version", "1.3");
                    tls.addProperty("max_version", "1.3");
                    tls.addProperty("certificate_path", certPath);
                    tls.addProperty("key_path", keyPath);

                    JsonObject h2 = new JsonObject();
                    h2.addProperty("type", "hysteria2");
                    h2.addProperty("tag", "hysteria2");
                    h2.addProperty("listen", "0.0.0.0");
                    h2.addProperty("listen_port", Integer.parseInt(port));
                    h2.add("users", users);
                    h2.addProperty("ignore_client_bandwidth", false);
                    h2.addProperty("masquerade", "https://bing.com");
                    h2.add("tls", tls);
                    inbounds.add(h2);
                }
            }
        }

        // TUIC inbound
        if (!tuicPort.isEmpty()) {
            for (String port : tuicPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("uuid", uuid);
                    user.addProperty("password", uuid);
                    JsonArray users = new JsonArray();
                    users.add(user);

                    JsonArray alpn = new JsonArray();
                    alpn.add("h3");

                    JsonObject tls = new JsonObject();
                    tls.addProperty("enabled", true);
                    tls.add("alpn", alpn);
                    tls.addProperty("certificate_path", certPath);
                    tls.addProperty("key_path", keyPath);

                    JsonObject tuic = new JsonObject();
                    tuic.addProperty("type", "tuic");
                    tuic.addProperty("tag", "tuic");
                    tuic.addProperty("listen", "0.0.0.0");
                    tuic.addProperty("listen_port", Integer.parseInt(port));
                    tuic.add("users", users);
                    tuic.addProperty("congestion_control", "bbr");
                    tuic.addProperty("zero_rtt_handshake", false);
                    tuic.add("tls", tls);
                    inbounds.add(tuic);
                }
            }
        }

        // VLESS Reality inbound
        if (!realityPort.isEmpty() && !realityPrivateKey.isEmpty()) {
            String realityServer = env.getOrDefault("REALITY_DEST", "www.google.com");
            for (String port : realityPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    JsonObject user = new JsonObject();
                    user.addProperty("uuid", uuid);
                    user.addProperty("flow", "xtls-rprx-vision");
                    JsonArray users = new JsonArray();
                    users.add(user);

                    JsonObject handshake = new JsonObject();
                    handshake.addProperty("server", realityServer);
                    handshake.addProperty("server_port", 443);

                    JsonObject reality = new JsonObject();
                    reality.addProperty("enabled", true);
                    reality.add("handshake", handshake);
                    reality.addProperty("private_key", realityPrivateKey);
                    JsonArray shortIds = new JsonArray();
                    shortIds.add(realityShortId);
                    reality.add("short_id", shortIds);

                    JsonObject tls = new JsonObject();
                    tls.addProperty("enabled", true);
                    tls.addProperty("server_name", realityServer);
                    tls.add("reality", reality);

                    JsonObject vless = new JsonObject();
                    vless.addProperty("type", "vless");
                    vless.addProperty("tag", "vless-reality");
                    vless.addProperty("listen", "0.0.0.0");
                    vless.addProperty("listen_port", Integer.parseInt(port));
                    vless.add("users", users);
                    vless.add("tls", tls);
                    inbounds.add(vless);
                }
            }
        }

        // Build top-level config
        JsonObject ntp = new JsonObject();
        ntp.addProperty("enabled", true);
        ntp.addProperty("server", "time.apple.com");
        ntp.addProperty("server_port", 123);
        ntp.addProperty("interval", "60m");

        JsonObject localDns = new JsonObject();
        localDns.addProperty("tag", "local");
        localDns.addProperty("type", "local");
        JsonArray dnsServers = new JsonArray();
        dnsServers.add(localDns);
        JsonObject dns = new JsonObject();
        dns.add("servers", dnsServers);
        dns.addProperty("strategy", NetworkDetector.detectDNSStrategy());

        JsonObject directOutbound = new JsonObject();
        directOutbound.addProperty("type", "direct");
        directOutbound.addProperty("tag", "direct");
        JsonArray outbounds = new JsonArray();
        outbounds.add(directOutbound);

        JsonObject log = new JsonObject();
        log.addProperty("level", "error");

        JsonObject config = new JsonObject();
        config.add("log", log);
        config.add("ntp", ntp);
        config.add("dns", dns);
        config.add("inbounds", inbounds);
        config.add("outbounds", outbounds);

        String configJson = GSON.toJson(config);

        Path configPath = Paths.get(System.getProperty("java.io.tmpdir"), "sing-box-config.json");
        Files.write(configPath, configJson.getBytes("UTF-8"));
        return configPath;
    }

    // ==================== Reality Key Generation ====================

    /**
     * Generates Reality keypair using sing-box CLI if keys are not already provided.
     * Populates REALITY_PRIVATE_KEY, REALITY_SHORT_ID, and REALITY_PUBLIC_KEY in the map.
     */
    public static void generateRealityKeysIfNeeded(Map<String, String> env) throws Exception {
        String privateKey = env.getOrDefault("REALITY_PRIVATE_KEY", "");
        String shortId = env.getOrDefault("REALITY_SHORT_ID", "");

        if (!privateKey.isEmpty() && !shortId.isEmpty()) {
            Log.info("[SBX] Using provided Reality keys");
            return;
        }

        String realityPort = env.getOrDefault("REALITY_PORT", "");
        if (realityPort.isEmpty()) {
            Log.info("[SBX] No REALITY_PORT set, skipping Reality key generation");
            return;
        }

        Log.info("[SBX] Generating Reality keypair...");
        Path singBoxPath = getBinaryPath();
        ProcessBuilder pb = new ProcessBuilder(singBoxPath.toString(), "generate", "reality-keypair");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String privKey = null;
        String pubKey = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.info("[SBX] " + line);
                if (line.startsWith("PrivateKey")) {
                    privKey = line.substring(line.indexOf(':') + 1).trim();
                } else if (line.startsWith("PublicKey")) {
                    pubKey = line.substring(line.indexOf(':') + 1).trim();
                }
            }
        }
        proc.waitFor();

        if (privKey == null || pubKey == null) {
            throw new IOException("Failed to generate Reality keypair");
        }

        if (privateKey.isEmpty()) {
            env.put("REALITY_PRIVATE_KEY", privKey);
        }
        env.put("REALITY_PUBLIC_KEY", pubKey);

        Log.info("[SBX] Reality PublicKey: " + pubKey);
        Log.info("[SBX] Reality ShortId: " + env.get("REALITY_SHORT_ID"));
        Log.info("[SBX] (PrivateKey saved in config, not printed for security)");
    }

    // ==================== Binary Management ====================

    /**
     * Returns the path to the sing-box binary, downloading and extracting it if necessary.
     * Supports Linux and macOS (tar.gz).
     */
    public static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String osName = System.getProperty("os.name").toLowerCase();
        String version = "1.13.14";
        String arch;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "amd64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "arm64";
        } else if (osArch.contains("s390x")) {
            arch = "s390x";
        } else if (osArch.contains("arm")) {
            arch = "armv7";
        } else {
            throw new IOException("Unsupported architecture: " + osArch);
        }

        String platform = osName.contains("linux") ? "linux" :
                          osName.contains("mac") ? "darwin" : "linux";

        String filename = "sing-box-" + version + "-" + platform + "-" + arch + ".tar.gz";
        String url = "https://github.com/SagerNet/sing-box/releases/download/v" + version + "/" + filename;

        Path tarPath = Paths.get(System.getProperty("java.io.tmpdir"), "sbx.tar.gz");
        Path binPath = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");

        if (!binPath.toFile().exists()) {
            Log.info("[SBX] Downloading sing-box " + version + " (" + arch + ")...");
            boolean downloaded = false;
            IOException lastErr = null;
            String[] mirrors = {"https://mirror.ghproxy.com/" + url, "https://ghproxy.net/" + url, url};
            Log.info("[SBX] Attempting download (" + mirrors.length + " mirrors)...");
            for (int m = 0; m < mirrors.length && !downloaded; m++) {
                for (int attempt = 1; attempt <= 3 && !downloaded; attempt++) {
                    try {
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(mirrors[m]).openConnection();
                        try {
                            conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
                            try (InputStream in = conn.getInputStream()) { Files.copy(in, tarPath, StandardCopyOption.REPLACE_EXISTING); }
                            downloaded = true;
                            Log.info("[SBX] Download succeeded from mirror " + (m+1) + " attempt " + attempt);
                        } finally { conn.disconnect(); }
                    } catch (IOException e) {
                        lastErr = e;
                        if (attempt < 3) {
                            try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    }
                }
            }
            if (!downloaded) { throw new IOException("[SBX] Failed to download sing-box after all mirrors/retries", lastErr); }

            Path extractDir = Paths.get(System.getProperty("java.io.tmpdir"), "sbx_extract_" + System.currentTimeMillis());
            Files.createDirectories(extractDir);

            ProcessBuilder pb = new ProcessBuilder("tar", "xzf", tarPath.toAbsolutePath().toString(),
                "-C", extractDir.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            int exitCode;
            try {
                exitCode = pb.start().waitFor();
            } catch (InterruptedException e) {
                throw new IOException("Interrupted while extracting sing-box");
            }

            if (exitCode != 0) {
                throw new IOException("Failed to extract sing-box binary");
            }

            Path found = findBinary(extractDir, "sing-box");
            if (found == null) {
                throw new IOException("sing-box binary not found in archive");
            }
            Files.move(found, binPath, StandardCopyOption.REPLACE_EXISTING);

            Files.deleteIfExists(tarPath);
            deleteDir(extractDir);
        }

        if (!binPath.toFile().setExecutable(true)) {
            throw new IOException("Failed to set executable permission");
        }
        return binPath;
    }

    private static Path findBinary(Path dir, String name) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.getFileName().toString().equals(name)) {
                    return p;
                }
                if (Files.isDirectory(p)) {
                    Path found = findBinary(p, name);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static void deleteDir(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {
                    Log.warn("[SBX] Cannot delete file during cleanup: " + p);
                }});
        } catch (Exception e) {
            Log.warn("[SBX] Failed to walk directory during cleanup: " + dir);
        }
    }
}
