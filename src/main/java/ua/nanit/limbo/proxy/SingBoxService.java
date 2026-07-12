package ua.nanit.limbo.proxy;

import ua.nanit.limbo.server.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * sing-box 服务：用单一二进制替代 Xray + Hysteria2 + TUIC 等
 * 支持协议：VLESS+WS, VLESS+Reality, Hysteria2, TUIC, Shadowsocks, Trojan
 */
public class SingBoxService extends AbstractProxyService {

    private static final String APP_NAME = "sb";
    private static final String APP_CONFIG_NAME = "config.json";
    private static final java.nio.file.Path NODE_FILE_PATH = Paths.get(System.getProperty("user.dir"), "node.txt");

    // 节点链接模板
    private static final String WS_URL = "vless://%s@%s:443?encryption=none&security=tls&sni=%s&fp=chrome&type=ws&path=%%2F%%3Fed%%3D2560#%s-ws-argo";
    private static final String REALITY_URL = "vless://%s@%s:%s?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.cloudflare.com&fp=chrome&pbk=%s&sid=%s&spx=%%2F&type=tcp&headerType=none#%s-reality";
    private static final String HY2_URL = "hysteria2://%s@%s:%s?insecure=1#%s-hy2";
    private static final String TUIC_URL = "tuic://%s:%s@%s:%s?congestion_control=bbr&alpn=h3&udp_relay_mode=native&sni=%s&allow_insecure=1#%s-tuic";
    private static final String SS_URL = "ss://%s@%s:%s#%s-ss";
    private static final String TROJAN_URL = "trojan://%s@%s:%s?security=tls&sni=%s&allow_insecure=1#%s-trojan";

    public SingBoxService(ProxyConfig config) {
        super(config);
    }

    @Override
    protected String getAppDownloadUrl() {
        String version = config.getSingboxVersion();
        String arch = OS_IS_ARM ? "arm64" : "amd64";
        return "https://github.com/SagerNet/sing-box/releases/download/v" + version
                + "/sing-box-" + version + "-linux-" + arch + ".tar.gz";
    }

    @Override
    public void install() throws Exception {
        Log.info("Installing sing-box service...");
        File binaryPath = initBinaryPath();
        File binaryFile = new File(binaryPath, APP_NAME);

        if (binaryFile.exists()) {
            Log.info("sing-box binary already exists, skipping download");
        } else {
            File archiveFile = new File(binaryPath, "sing-box.tar.gz");
            download(getAppDownloadUrl(), archiveFile);
            extractTarGz(archiveFile, binaryPath);

            // 查找解压后的 sing-box 可执行文件
            String version = config.getSingboxVersion();
            String extractedDirName = "sing-box-" + version + "-linux-" + (OS_IS_ARM ? "arm64" : "amd64");
            File extractedBinary = new File(binaryPath, extractedDirName + "/sing-box");
            if (!extractedBinary.exists()) {
                // 尝试直接在 binaryPath 下找
                extractedBinary = new File(binaryPath, "sing-box");
            }
            if (extractedBinary.exists()) {
                if (!extractedBinary.renameTo(binaryFile)) {
                    // 如果 rename 失败（跨目录），用 copy
                    Files.copy(extractedBinary.toPath(), binaryFile.toPath());
                }
            }
            archiveFile.delete();
            setExecutePermission(binaryFile);
            Log.info("sing-box binary installed: " + binaryFile.getPath());
        }

        // 生成 Reality 密钥
        generateRealityKeys(binaryFile);
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        config.setRealityShortId(shortId);

        // 生成 TLS 自签证书（Hy2/TUIC/Trojan 用）
        if (config.isHy2Enabled() || config.isTuicEnabled() || config.isTrojanEnabled()) {
            TlsCertGenerator.generate(config.getDomain(), 3650, 2048, binaryPath);
            Log.info("TLS certificate generated for sing-box");
        }

        // 生成 sing-box 配置
        generateConfig(binaryPath);
        Log.info("sing-box service installed successfully");
    }

    private void extractTarGz(File archiveFile, File destDir) throws IOException {
        Log.info("Extracting " + archiveFile.getName() + "...");
        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", archiveFile.getAbsolutePath(),
                "-C", destDir.getAbsolutePath());
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrupted", e);
        }
    }

    private void generateRealityKeys(File binaryFile) throws Exception {
        if (config.getRealityPublicKey() != null && !config.getRealityPublicKey().isEmpty()
                && config.getRealityPrivateKey() != null && !config.getRealityPrivateKey().isEmpty()) {
            Log.info("Reality keys already configured, skipping generation");
            return;
        }

        Log.info("Generating Reality keys...");
        ProcessBuilder pb = new ProcessBuilder(binaryFile.getAbsolutePath(), "generate", "reality-keypair");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();

        String outputStr = output.toString();
        // sing-box 输出格式: PrivateKey: xxx  /  PublicKey: xxx
        String privateKey = extractValue(outputStr, "PrivateKey:");
        String publicKey = extractValue(outputStr, "PublicKey:");

        if (privateKey == null || publicKey == null) {
            Log.warn("Failed to parse reality keys output: " + outputStr);
            throw new RuntimeException("Failed to generate reality keys");
        }

        config.setRealityPrivateKey(privateKey);
        config.setRealityPublicKey(publicKey);
        Log.info("Reality keys generated successfully");
    }

    private String extractValue(String output, String key) {
        int idx = output.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        while (start < output.length() && (output.charAt(start) == ' ' || output.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < output.length() && output.charAt(end) != '\n' && output.charAt(end) != '\r') {
            end++;
        }
        return output.substring(start, end).trim();
    }

    /**
     * 生成 sing-box 配置文件（JSON）
     * 根据启用的协议动态生成 inbounds
     */
    private void generateConfig(File binaryPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"log\": {\n");
        sb.append("    \"level\": \"warn\",\n");
        sb.append("    \"timestamp\": true\n");
        sb.append("  },\n");
        sb.append("  \"inbounds\": [\n");

        List<String> inbounds = new ArrayList<>();
        String certPath = binaryPath.getAbsolutePath() + "/cert.pem";
        String keyPath = binaryPath.getAbsolutePath() + "/key.pem";

        // 1. VLESS + WS（Argo 隧道转发用）
        if (config.getWsPort() != null && !config.getWsPort().isEmpty()) {
            inbounds.add(buildVlessWsInbound());
        }

        // 2. VLESS + Reality
        if (config.isRealityEnabled()) {
            inbounds.add(buildVlessRealityInbound());
        }

        // 3. Hysteria2
        if (config.isHy2Enabled()) {
            inbounds.add(buildHy2Inbound(certPath, keyPath));
        }

        // 4. TUIC
        if (config.isTuicEnabled()) {
            inbounds.add(buildTuicInbound(certPath, keyPath));
        }

        // 5. Shadowsocks
        if (config.isSsEnabled()) {
            inbounds.add(buildShadowsocksInbound());
        }

        // 6. Trojan
        if (config.isTrojanEnabled()) {
            inbounds.add(buildTrojanInbound(certPath, keyPath));
        }

        for (int i = 0; i < inbounds.size(); i++) {
            sb.append(inbounds.get(i));
            if (i < inbounds.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ],\n");
        sb.append("  \"outbounds\": [\n");
        sb.append("    {\n");
        sb.append("      \"type\": \"direct\",\n");
        sb.append("      \"tag\": \"direct\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}");

        File configFile = new File(binaryPath, APP_CONFIG_NAME);
        try (FileWriter writer = new FileWriter(configFile, StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }
        Log.info("sing-box config generated: " + configFile.getPath());

        // 生成节点分享链接
        generateNodeLinks();
    }

    private String buildVlessWsInbound() {
        return "    {\n" +
                "      \"type\": \"vless\",\n" +
                "      \"tag\": \"vless-ws-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getWsPort() + ",\n" +
                "      \"users\": [\n" +
                "        {\n" +
                "          \"uuid\": \"" + config.getUuid() + "\",\n" +
                "          \"flow\": \"\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"transport\": {\n" +
                "        \"type\": \"ws\",\n" +
                "        \"path\": \"/\"\n" +
                "      }\n" +
                "    }";
    }

    private String buildVlessRealityInbound() {
        return "    {\n" +
                "      \"type\": \"vless\",\n" +
                "      \"tag\": \"vless-reality-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getRealityPort() + ",\n" +
                "      \"users\": [\n" +
                "        {\n" +
                "          \"uuid\": \"" + config.getUuid() + "\",\n" +
                "          \"flow\": \"xtls-rprx-vision\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"tls\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"server_name\": \"www.cloudflare.com\",\n" +
                "        \"reality\": {\n" +
                "          \"enabled\": true,\n" +
                "          \"handshake\": {\n" +
                "            \"server\": \"www.cloudflare.com\",\n" +
                "            \"server_port\": 443\n" +
                "          },\n" +
                "          \"private_key\": \"" + config.getRealityPrivateKey() + "\",\n" +
                "          \"short_id\": [\"" + config.getRealityShortId() + "\"]\n" +
                "        }\n" +
                "      }\n" +
                "    }";
    }

    private String buildHy2Inbound(String certPath, String keyPath) {
        return "    {\n" +
                "      \"type\": \"hysteria2\",\n" +
                "      \"tag\": \"hy2-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getHy2Port() + ",\n" +
                "      \"users\": [\n" +
                "        {\n" +
                "          \"password\": \"" + config.getUuid() + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"tls\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"certificate_path\": \"" + certPath + "\",\n" +
                "        \"key_path\": \"" + keyPath + "\"\n" +
                "      }\n" +
                "    }";
    }

    private String buildTuicInbound(String certPath, String keyPath) {
        return "    {\n" +
                "      \"type\": \"tuic\",\n" +
                "      \"tag\": \"tuic-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getTuicPort() + ",\n" +
                "      \"users\": [\n" +
                "        {\n" +
                "          \"uuid\": \"" + config.getUuid() + "\",\n" +
                "          \"password\": \"" + config.getTuicPassword() + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"congestion_control\": \"bbr\",\n" +
                "      \"tls\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"certificate_path\": \"" + certPath + "\",\n" +
                "        \"key_path\": \"" + keyPath + "\"\n" +
                "      }\n" +
                "    }";
    }

    private String buildShadowsocksInbound() {
        return "    {\n" +
                "      \"type\": \"shadowsocks\",\n" +
                "      \"tag\": \"ss-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getSsPort() + ",\n" +
                "      \"method\": \"2022-blake3-aes-128-gcm\",\n" +
                "      \"password\": \"" + config.getSsPassword() + "\"\n" +
                "    }";
    }

    private String buildTrojanInbound(String certPath, String keyPath) {
        return "    {\n" +
                "      \"type\": \"trojan\",\n" +
                "      \"tag\": \"trojan-in\",\n" +
                "      \"listen\": \"::\",\n" +
                "      \"listen_port\": " + config.getTrojanPort() + ",\n" +
                "      \"users\": [\n" +
                "        {\n" +
                "          \"password\": \"" + config.getTrojanPassword() + "\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"tls\": {\n" +
                "        \"enabled\": true,\n" +
                "        \"certificate_path\": \"" + certPath + "\",\n" +
                "        \"key_path\": \"" + keyPath + "\"\n" +
                "      }\n" +
                "    }";
    }

    /**
     * 生成所有协议的节点分享链接，保存到 node.txt
     */
    private void generateNodeLinks() throws IOException {
        List<String> links = new ArrayList<>();
        String prefix = config.getRemarksPrefix();
        String domain = config.getDomain();

        // VLESS+WS（走 Argo 隧道，使用 argoDomain 或 cfIp）
        String wsAddr = config.getArgoDomain();
        if (wsAddr == null || wsAddr.isEmpty()) {
            wsAddr = config.getCfIp();
        }
        if (!wsAddr.isEmpty()) {
            links.add(String.format(WS_URL, config.getUuid(), wsAddr, wsAddr, prefix));
        }

        // VLESS+Reality
        if (config.isRealityEnabled()) {
            links.add(String.format(REALITY_URL, config.getUuid(), domain,
                    config.getRealityPort(), config.getRealityPublicKey(),
                    config.getRealityShortId(), prefix));
        }

        // Hysteria2
        if (config.isHy2Enabled()) {
            links.add(String.format(HY2_URL, config.getUuid(), domain,
                    config.getHy2Port(), prefix));
        }

        // TUIC
        if (config.isTuicEnabled()) {
            links.add(String.format(TUIC_URL, config.getUuid(), config.getTuicPassword(),
                    domain, config.getTuicPort(), domain, prefix));
        }

        // Shadowsocks (需要 base64 编码 method:password)
        if (config.isSsEnabled()) {
            String ssUserInfo = java.util.Base64.getEncoder()
                    .encodeToString(("2022-blake3-aes-128-gcm:" + config.getSsPassword())
                            .getBytes(StandardCharsets.UTF_8));
            links.add(String.format(SS_URL, ssUserInfo, domain, config.getSsPort(), prefix));
        }

        // Trojan
        if (config.isTrojanEnabled()) {
            links.add(String.format(TROJAN_URL, config.getTrojanPassword(), domain,
                    config.getTrojanPort(), domain, prefix));
        }

        Files.write(NODE_FILE_PATH, links);
        Log.info("Node links saved to " + NODE_FILE_PATH + " (" + links.size() + " nodes)");
    }

    @Override
    public void startup() throws Exception {
        File binaryPath = getBinaryPath();
        File appFile = new File(binaryPath, APP_NAME);
        File configFile = new File(binaryPath, APP_CONFIG_NAME);

        if (!appFile.exists()) {
            throw new IOException("sing-box binary not found: " + appFile.getPath());
        }

        ProcessBuilder pb = new ProcessBuilder(
                appFile.getAbsolutePath(),
                "run", "-c", configFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);

        Log.info("Starting sing-box...");
        startProcessAsync(pb);
    }

    @Override
    protected String getAppName() {
        return APP_NAME;
    }
}
