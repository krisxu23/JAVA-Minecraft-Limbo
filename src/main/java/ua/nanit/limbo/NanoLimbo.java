package ua.nanit.limbo;

import ua.nanit.limbo.server.Log;
import ua.nanit.limbo.server.LimboServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class NanoLimbo {
    private static Process singBoxProcess;
    private static Process cloudflaredProcess;
    private static final Path LIB_DIR = Paths.get(System.getProperty("user.dir"), "lib");
    private static final Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");
    private static volatile String tunnelDomain = null;
    private static volatile boolean argoReady = false;

    private static String UUID_VAL = "2523c510-9ff0-415b-9582-93949bfae7e3";
    private static String DOMAIN = "";
    private static String PORT = "25565";
    private static String NAME = "";
    private static String WS_PORT = "8001";
    private static String REALITY_PORT = "";
    private static String HY2_PORT = "";
    private static String TUIC_PORT = "";
    private static String S5_PORT = "";
    private static String ANYTLS_PORT = "";
    private static String CFIP = "spring.io";
    private static String CF_PORT = "443";
    private static String ARGO_DOMAIN = "";
    private static String ARGO_TOKEN = "";
    private static boolean DISABLE_ARGO = false;
    private static String REALITY_PRIV_KEY = "";
    private static String REALITY_PUB_KEY = "";
    private static String REALITY_SHORT_ID = "";
    private static String TUIC_PASSWORD = "";
    private static String S5_USER = "xah";
    private static String S5_PASSWORD = "";
    private static String ANYTLS_PASSWORD = "";
    private static String SB_VERSION = "1.10.0-alpha.7";
    private static String arch;

    public static void main(String[] args) {
        float classVersion = Float.parseFloat(System.getProperty("java.class.version"));
        float javaVersion = classVersion - 44;
        Log.info("[NanoLimbo] Detected Java %.0f (class version %.0f)", javaVersion, classVersion);

        if (classVersion < 52.0) {
            System.err.println("ERROR: Java 8+ required");
            System.exit(1);
        }

        try {
            Files.createDirectories(LIB_DIR);
            arch = detectArch();
            loadEnv();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("[NanoLimbo] Shutting down...");
                stopServices();
                Log.info("[NanoLimbo] Goodbye");
            }));

            Log.info("[NanoLimbo] ========================================");
            Log.info("[NanoLimbo] Minecraft-Limbo Proxy Wrapper");
            Log.info("[NanoLimbo] ========================================");

            generateCert();
            startSingBox();
            if (!DISABLE_ARGO) {
                startCloudflared();
            } else {
                Log.info("[NanoLimbo] Argo tunnel disabled");
            }

            // Wait for Argo tunnel to be ready before saving nodes
            waitForArgoReady(45000);

            // Write all node links to players.dat
            saveNodeLinks();

            Log.info("[NanoLimbo] Starting enhanced Minecraft camouflage...");
            new LimboServer().start();

        } catch (Exception e) {
            Log.error("[NanoLimbo] Fatal error: ", e);
            System.exit(1);
        }
    }

    private static void loadEnv() {
        UUID_VAL = env("UUID", UUID_VAL);
        DOMAIN = env("DOMAIN", "");
        PORT = env("PORT", "25565");
        NAME = env("NAME", "");
        WS_PORT = env("ARGO_PORT", "8001");
        REALITY_PORT = env("REALITY_PORT", "");
        HY2_PORT = env("HY2_PORT", "");
        TUIC_PORT = env("TUIC_PORT", "");
        S5_PORT = env("S5_PORT", "");
        ANYTLS_PORT = env("ANYTLS_PORT", "");
        SB_VERSION = env("SB_VERSION", "1.10.0-alpha.7");
        CFIP = env("CFIP", "spring.io");
        CF_PORT = env("CFPORT", "443");
        ARGO_DOMAIN = env("ARGO_DOMAIN", "");
        ARGO_TOKEN = env("ARGO_AUTH", "");
        DISABLE_ARGO = "true".equalsIgnoreCase(env("DISABLE_ARGO", "false"));
        TUIC_PASSWORD = env("TUIC_PASSWORD", UUID_VAL);
        S5_USER = env("S5_USER", "proxy");
        S5_PASSWORD = env("S5_PASSWORD", UUID_VAL);
        ANYTLS_PASSWORD = env("ANYTLS_PASSWORD", UUID_VAL);

        if (DOMAIN.isEmpty()) DOMAIN = fetchPublicIp();
        generateRealityKeypair();
    }

    private static void generateRealityKeypair() {
        REALITY_SHORT_ID = UUID.randomUUID().toString().substring(0, 8);
        Log.info("[NanoLimbo] Generated session keys (short_id=%s)", REALITY_SHORT_ID);
    }

    private static void startSingBox() throws Exception {
        String sbVersion = SB_VERSION;
        Path sbBinary = downloadSingBox(sbVersion);
        Path configPath = LIB_DIR.resolve("config.json");
        generateSingBoxConfig(configPath);

        // Validate config first
        ProcessBuilder validate = new ProcessBuilder(sbBinary.toString(), "check", "-c", configPath.toString());
        validate.redirectErrorStream(true);
        Process vp = validate.start();
        vp.waitFor(5, TimeUnit.SECONDS);

        Log.info("[NanoLimbo] Starting sing-box proxy...");
        ProcessBuilder pb = new ProcessBuilder(sbBinary.toString(), "run", "-c", configPath.toString());
        pb.environment().put("SBX_LOG_LEVEL", "fatal");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.to(LIB_DIR.resolve("native.log").toFile()));
        singBoxProcess = pb.start();

        Thread.sleep(3000);
        Log.info("[NanoLimbo] Sing-box started (PID: %d)", singBoxProcess.pid());
    }

    private static void startCloudflared() throws Exception {
        Path cfBinary = downloadCloudflared();
        boolean fixedTunnel = ARGO_DOMAIN != null && !ARGO_DOMAIN.isEmpty();

        if (fixedTunnel) {
            Log.info("[NanoLimbo] Cloudflare: fixed tunnel mode (domain=%s)", ARGO_DOMAIN);
            tunnelDomain = ARGO_DOMAIN;
            argoReady = true;
            ProcessBuilder pb = new ProcessBuilder(cfBinary.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--loglevel", "error",
                "run", "--token", ARGO_TOKEN);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(LIB_DIR.resolve("cloudflared.log").toFile()));
            cloudflaredProcess = pb.start();
            Log.info("[NanoLimbo] Cloudflare fixed tunnel started (PID: %d)", cloudflaredProcess.pid());
        } else {
            Log.info("[NanoLimbo] Cloudflare: temporary tunnel mode (wsPort=%s)", WS_PORT);
            Path bridgeLog = LIB_DIR.resolve("bridge.log");
            ProcessBuilder pb = new ProcessBuilder(cfBinary.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--loglevel", "error",
                "--url", "http://localhost:" + WS_PORT);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(bridgeLog.toFile()));
            cloudflaredProcess = pb.start();
            Log.info("[NanoLimbo] Cloudflare temporary tunnel started (PID: %d)", cloudflaredProcess.pid());

            // Poll bridge.log for tunnel domain
            startBridgeWatcher(bridgeLog);
        }
    }

    private static void startBridgeWatcher(Path bridgeLog) {
        Thread poller = new Thread(() -> {
            Pattern domainPattern = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
            long deadline = System.currentTimeMillis() + 60_000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (Files.exists(bridgeLog)) {
                        List<String> lines = Files.readAllLines(bridgeLog, StandardCharsets.UTF_8);
                        for (String line : lines) {
                            Matcher m = domainPattern.matcher(line);
                            String lastMatch = null;
                            while (m.find()) lastMatch = m.group();
                            if (lastMatch != null) {
                                String domain = new URL(lastMatch).getHost();
                                tunnelDomain = domain;
                                argoReady = true;
                                Log.info("[NanoLimbo] Cloudflare tunnel endpoint: %s", domain);
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
                try { Thread.sleep(1500); } catch (InterruptedException ie) { return; }
            }
            Log.warn("[NanoLimbo] Cloudflare timed out waiting for tunnel endpoint, using fallback");
            // Fallback: try to get from stdout even after timeout
            if (!argoReady && Files.exists(bridgeLog)) {
                try {
                    List<String> lines = Files.readAllLines(bridgeLog, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        Matcher m = domainPattern.matcher(line);
                        String lastMatch = null;
                        while (m.find()) lastMatch = m.group();
                        if (lastMatch != null) {
                            tunnelDomain = new URL(lastMatch).getHost();
                            argoReady = true;
                            Log.info("[NanoLimbo] Cloudflare tunnel endpoint (delayed): %s", tunnelDomain);
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }, "bridge-watcher");
        poller.setDaemon(true);
        poller.start();
    }

    private static void waitForArgoReady(long timeoutMs) {
        long start = System.currentTimeMillis();
        while (!argoReady && (System.currentTimeMillis() - start) < timeoutMs) {
            try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
        }
        if (argoReady) {
            Log.info("[NanoLimbo] Cloudflare tunnel ready (domain=%s)", tunnelDomain);
        } else {
            Log.warn("[NanoLimbo] Cloudflare tunnel not ready after %d ms, proceeding without tunnel domain", timeoutMs);
        }
    }

    private static Path downloadSingBox(String version) throws Exception {
        String archStr = arch;
        Path binaryPath = LIB_DIR.resolve("sbx");

        if (Files.exists(binaryPath) && Files.size(binaryPath) > 0) {
            Log.info("[NanoLimbo] Sing-box binary found");
            return binaryPath;
        }

        Log.info("[NanoLimbo] Downloading sing-box v%s (%s)...", version, archStr);
        String url = "https://github.com/SagerNet/sing-box/releases/download/v" + version
            + "/sing-box-" + version + "-linux-" + archStr + ".tar.gz";
        Path archivePath = LIB_DIR.resolve("sing-box.tar.gz");
        downloadFile(url, archivePath);

        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", archivePath.toString(),
            "-C", LIB_DIR.toString(), "--strip-components=1",
            "sing-box-" + version + "-linux-" + archStr + "/sing-box");
        pb.start().waitFor();
        Files.deleteIfExists(archivePath);
        Files.move(LIB_DIR.resolve("sing-box"), binaryPath, StandardCopyOption.REPLACE_EXISTING);
        binaryPath.toFile().setExecutable(true);
        Log.info("[NanoLimbo] Sing-box downloaded successfully");
        return binaryPath;
    }

    private static Path downloadCloudflared() throws Exception {
        String archStr = arch;
        Path binaryPath = LIB_DIR.resolve("net");

        if (Files.exists(binaryPath) && Files.size(binaryPath) > 0) {
            Log.info("[NanoLimbo] Cloudflare binary found");
            return binaryPath;
        }

        Log.info("[NanoLimbo] Downloading cloudflared (%s)...", archStr);
        String url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-" + archStr;
        downloadFile(url, binaryPath);
        binaryPath.toFile().setExecutable(true);
        Log.info("[NanoLimbo] Cloudflare downloaded successfully");
        return binaryPath;
    }

    private static void generateSingBoxConfig(Path configPath) throws IOException {
        List<String> inbounds = new ArrayList<>();

        // WS inbound (for Argo tunnel)
        if (!WS_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"vmess\",\"tag\":\"in-ws\",\"listen\":\"::\",\"listen_port\":" + WS_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"alterId\":0}]"
                + ",\"transport\":{\"type\":\"ws\",\"path\":\"/\",\"max_early_data\":2560,\"early_data_header_name\":\"Sec-WebSocket-Protocol\"}}");
        }

        // VLESS+Reality inbound
        if (!REALITY_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"vless\",\"tag\":\"in-reality\",\"listen\":\"::\",\"listen_port\":" + REALITY_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"flow\":\"xtls-rprx-vision\"}]"
                + ",\"tls\":{\"enabled\":true,\"server_name\":\"www.cloudflare.com\""
                + ",\"reality\":{\"enabled\":true,\"handshake\":{\"server\":\"www.cloudflare.com\",\"server_port\":443}"
                + ",\"private_key\":\"" + REALITY_PRIV_KEY + "\",\"short_id\":[\"" + REALITY_SHORT_ID + "\"]}}}");
        }

        // Hysteria2 inbound
        if (!HY2_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"hysteria2\",\"tag\":\"in-hy2\",\"listen\":\"::\",\"listen_port\":" + HY2_PORT
                + ",\"users\":[{\"password\":\"" + UUID_VAL + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        // TUIC inbound
        if (!TUIC_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"tuic\",\"tag\":\"in-tuic\",\"listen\":\"::\",\"listen_port\":" + TUIC_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"password\":\"" + TUIC_PASSWORD + "\"}]"
                + ",\"congestion_control\":\"bbr\""
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        // SOCKS5 inbound
        if (!S5_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"socks\",\"tag\":\"in-s5\",\"listen\":\"::\",\"listen_port\":" + S5_PORT
                + ",\"users\":[{\"username\":\"" + S5_USER + "\",\"password\":\"" + S5_PASSWORD + "\"}]}");
        }

        // AnyTLS inbound
        if (!ANYTLS_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"anytls\",\"tag\":\"in-anytls\",\"listen\":\"::\",\"listen_port\":" + ANYTLS_PORT
                + ",\"users\":[{\"password\":\"" + ANYTLS_PASSWORD + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"log\":{\"level\":\"fatal\",\"disabled\":true},\"inbounds\":[");
        for (int i = 0; i < inbounds.size(); i++) {
            sb.append(inbounds.get(i));
            if (i < inbounds.size() - 1) sb.append(",");
        }
        sb.append("],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}");
        sb.append("\n");

        Files.write(configPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        Log.info("[NanoLimbo] Sing-box config generated (%d inbounds)", inbounds.size());
    }

    private static void saveNodeLinks() throws IOException {
        List<String> links = new ArrayList<>();
        String d = DOMAIN.isEmpty() ? "127.0.0.1" : DOMAIN;
        String p = NAME.isEmpty() ? "Node" : NAME;

        // VMess+WS (Argo tunnel) - highest priority
        if (!WS_PORT.isEmpty() && tunnelDomain != null && !tunnelDomain.isEmpty()) {
            String wsAddr = (CFIP != null && !CFIP.isEmpty()) ? CFIP : tunnelDomain;
            String wsPort = (CF_PORT != null && !CF_PORT.isEmpty()) ? CF_PORT : "443";
            String json = "{\"v\":\"2\",\"ps\":\"" + p + "-vmess-ws-argo\",\"add\":\"" + wsAddr
                + "\",\"port\":\"" + wsPort + "\",\"id\":\"" + UUID_VAL + "\",\"aid\":\"0\",\"net\":\"ws\""
                + ",\"type\":\"none\",\"host\":\"" + tunnelDomain + "\",\"path\":\"/\""
                + ",\"tls\":\"tls\",\"sni\":\"" + tunnelDomain + "\"}";
            String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            links.add("vmess://" + b64);
        } else if (!WS_PORT.isEmpty()) {
            Log.warn("[NanoLimbo] WS_PORT configured but no tunnel domain available yet");
        }

        // VLESS+Reality
        if (!REALITY_PORT.isEmpty()) {
            links.add("vless://" + UUID_VAL + "@" + d + ":" + REALITY_PORT
                + "?encryption=none&flow=xtls-rprx-vision&security=reality"
                + "&sni=www.cloudflare.com&fp=chrome&pbk=" + REALITY_PUB_KEY
                + "&sid=" + REALITY_SHORT_ID + "&type=tcp&headerType=none#" + p + "-reality");
        }

        // Hysteria2
        if (!HY2_PORT.isEmpty()) {
            links.add("hysteria2://" + UUID_VAL + "@" + d + ":" + HY2_PORT + "?insecure=1#" + p + "-hy2");
        }

        // TUIC
        if (!TUIC_PORT.isEmpty()) {
            links.add("tuic://" + UUID_VAL + ":" + TUIC_PASSWORD + "@" + d + ":" + TUIC_PORT
                + "?congestion_control=bbr&alpn=h3&udp_relay_mode=native&sni=" + d + "&allow_insecure=1#" + p + "-tuic");
        }

        // SOCKS5
        if (!S5_PORT.isEmpty()) {
            links.add("socks5://" + S5_USER + ":" + S5_PASSWORD + "@" + d + ":" + S5_PORT + "#" + p + "-socks5");
        }

        // AnyTLS
        if (!ANYTLS_PORT.isEmpty()) {
            links.add("anytls://" + ANYTLS_PASSWORD + "@" + d + ":" + ANYTLS_PORT
                + "?security=tls&sni=" + d + "&allow_insecure=1#" + p + "-anytls");
        }

        if (links.isEmpty()) {
            Log.warn("[NanoLimbo] No proxy ports configured! Set at least one of: WS_PORT, REALITY_PORT, HY2_PORT, etc.");
            Files.write(DATA_FILE, new byte[0]);
            return;
        }

        // Write Base64 encoded
        StringBuilder combined = new StringBuilder();
        for (String l : links) combined.append(l).append("\n");
        String encoded = Base64.getEncoder().encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
        Log.info("[NanoLimbo] Player data saved (%d entries to players.dat)", links.size());
        for (String link : links) {
            String shortLink = link.length() > 80 ? link.substring(0, 80) + "..." : link;
            Log.info("[NanoLimbo]   - %s", shortLink);
        }
    }

    private static void updateDataFile(String domain) throws IOException {
        String wsAddr = (CFIP != null && !CFIP.isEmpty()) ? CFIP : domain;
        String wsPort = (CF_PORT != null && !CF_PORT.isEmpty()) ? CF_PORT : "443";
        String p = NAME.isEmpty() ? "Node" : NAME;
        String json = "{\"v\":\"2\",\"ps\":\"" + p + "-ws-argo\",\"add\":\"" + wsAddr
            + "\",\"port\":\"" + wsPort + "\",\"id\":\"" + UUID_VAL + "\",\"aid\":\"0\",\"net\":\"ws\""
            + ",\"type\":\"none\",\"host\":\"" + domain + "\",\"path\":\"/\""
            + ",\"tls\":\"tls\",\"sni\":\"" + domain + "\"}";
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String newLink = "vmess://" + b64;

        List<String> lines = new ArrayList<>();
        if (Files.exists(DATA_FILE)) {
            byte[] content = Files.readAllBytes(DATA_FILE);
            if (content.length > 0) {
                String decoded = new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8);
                for (String l : decoded.split("\n")) {
                    if (!l.trim().isEmpty() && !l.contains("-ws-argo")) lines.add(l);
                }
            }
        }
        lines.add(0, newLink);

        StringBuilder combined = new StringBuilder();
        for (String l : lines) combined.append(l).append("\n");
        String encoded = Base64.getEncoder().encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
        Log.info("[NanoLimbo] Player data updated with new tunnel domain: %s", domain);
    }

    private static void downloadFile(String url, Path target) throws IOException {
        Path tmp = LIB_DIR.resolve(target.getFileName() + ".download");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code + " for " + url);
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String detectArch() {
        String osArch = System.getProperty("os.arch", "");
        if (osArch.contains("aarch64") || osArch.contains("arm64")) return "arm64";
        return "amd64";
    }

    private static String fetchPublicIp() {
        String[] services = {"https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com"};
        for (String url : services) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "curl/8.0");
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String ip = reader.readLine();
                    reader.close();
                    if (ip != null && ip.matches("^[0-9a-fA-F.:]+$") && ip.length() >= 7) return ip.trim();
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    private static void generateCert() {
        boolean needCert = !HY2_PORT.isEmpty() || !TUIC_PORT.isEmpty() || !ANYTLS_PORT.isEmpty();
        if (!needCert) return;

        Path certPath = LIB_DIR.resolve("cert.pem");
        Path keyPath = LIB_DIR.resolve("key.pem");
        if (Files.exists(certPath) && Files.exists(keyPath)) return;

        try {
            Log.info("[NanoLimbo] Generating TLS certificate...");
            ProcessBuilder pb = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", keyPath.toString(), "-out", certPath.toString(),
                "-days", "3650", "-nodes", "-subj", "/CN=localhost");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            p.waitFor();
            Log.info("[NanoLimbo] TLS certificate generated");
        } catch (Exception e) {
            Log.warn("[NanoLimbo] Failed to generate cert: %s", e.getMessage());
        }
    }

    private static void stopServices() {
        if (cloudflaredProcess != null && cloudflaredProcess.isAlive()) {
            cloudflaredProcess.destroy();
            Log.info("[NanoLimbo] Cloudflare tunnel stopped");
        }
        if (singBoxProcess != null && singBoxProcess.isAlive()) {
            singBoxProcess.destroy();
            Log.info("[NanoLimbo] Sing-box stopped");
        }
    }
}
