package ua.nanit.limbo.proxy;

import ua.nanit.limbo.server.Log;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cloudflare Argo 隧道服务
 * 仅负责 cloudflared 隧道的运行
 * 节点链接由 SingBoxService 统一生成
 */
public class ArgoService extends AbstractProxyService {

    private static final String APP_NAME = "cf";
    private static final Pattern QUICK_TUNNEL_HOST_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static final String WS_URL = "vmess://%s";
    private static final java.nio.file.Path NODE_FILE_PATH = Paths.get(System.getProperty("user.dir"), "node.txt");

    public ArgoService(ProxyConfig config) {
        super(config);
    }

    @Override
    public String getAppDownloadUrl() {
        String arch = OS_IS_ARM ? "arm64" : "amd64";
        return "https://github.com/cloudflare/cloudflared/releases/download/" + config.getArgoVersion()
                + "/cloudflared-linux-" + arch;
    }

    @Override
    public void install() throws Exception {
        Log.info("Installing Argo service...");
        File binaryPath = initBinaryPath();
        File binaryFile = new File(binaryPath, APP_NAME);

        if (binaryFile.exists()) {
            Log.info("Argo binary already exists, skipping download");
        } else {
            download(getAppDownloadUrl(), binaryFile);
            setExecutePermission(binaryFile);
            Log.info("Argo binary installed: " + binaryFile.getPath());
        }
        Log.info("Argo service installed successfully");
    }

    @Override
    public void startup() throws Exception {
        File binaryFile = new File(getBinaryPath(), APP_NAME);
        if (!binaryFile.exists()) {
            throw new IOException("Argo binary not found: " + binaryFile.getPath());
        }

        // 如果有固定隧道域名，直接更新 node.txt 中的 WS 链接
        if (config.getArgoDomain() != null && !config.getArgoDomain().isEmpty()) {
            updateWsNodeLink(config.getArgoDomain());
            Log.info("Node links updated with Argo domain: " + config.getArgoDomain());
        }

        final String argoToken = config.getArgoToken();

        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Process process = null;
                try {
                    Log.info("Starting Argo...");
                    ProcessBuilder pb;

                    if (argoToken == null || argoToken.trim().isEmpty()) {
                        // 快速隧道模式
                        pb = new ProcessBuilder(
                                binaryFile.getAbsolutePath(), "tunnel", "--no-autoupdate",
                                "--edge-ip-version", "auto", "--protocol", "http2",
                                "--url", "http://localhost:" + config.getWsPort());
                    } else {
                        // 固定隧道模式
                        pb = new ProcessBuilder(
                                binaryFile.getAbsolutePath(), "tunnel", "--no-autoupdate",
                                "--edge-ip-version", "auto", "--protocol", "http2",
                                "run", "--token", argoToken);
                    }
                    pb.redirectErrorStream(true);
                    process = pb.start();

                    final Process finalProcess = process;
                    Thread logThread = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(finalProcess.getInputStream()))) {
                            String line;
                            boolean domainFound = config.getArgoDomain() != null && !config.getArgoDomain().isEmpty();
                            while ((line = reader.readLine()) != null) {
                                if (!domainFound) {
                                    Matcher matcher = QUICK_TUNNEL_HOST_PATTERN.matcher(line);
                                    String lastMatch = null;
                                    while (matcher.find()) {
                                        lastMatch = matcher.group();
                                    }
                                    if (lastMatch != null) {
                                        domainFound = true;
                                        try {
                                            String argoDomain = new URL(lastMatch).getHost();
                                            config.setArgoDomain(argoDomain);
                                            Log.info("Argo tunnel domain: " + argoDomain);
                                            updateWsNodeLink(argoDomain);
                                            Log.info("✅ Node links updated. View at: " + NODE_FILE_PATH);
                                        } catch (Exception e) {
                                            Log.error("Failed to parse argo domain: " + e.getMessage());
                                        }
                                    }
                                }
                                Log.info("[argo] " + line);
                            }
                        } catch (IOException ignored) {}
                    }, "argo-log");
                    logThread.setDaemon(true);
                    logThread.start();

                    int exitCode = process.waitFor();
                    Log.warn("Argo exited with code " + exitCode + ", restarting in 3s...");
                    Thread.sleep(3000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (process != null && process.isAlive()) process.destroyForcibly();
                    Log.info("Argo stopped");
                    break;
                } catch (Exception e) {
                    Log.error("Error running Argo: " + e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }, "argo-manager").start();
    }

    /**
     * 更新 node.txt 中的 WS/Argo 节点链接
     * 如果已有 ws-argo 链接则替换，否则追加
     */
    private void updateWsNodeLink(String argoDomain) throws IOException {
        String vmessJson = "{\"v\":\"2\",\"ps\":\"" + config.getRemarksPrefix() + "-ws-argo\","
                + "\"add\":\"" + argoDomain + "\","
                + "\"port\":\"443\","
                + "\"id\":\"" + config.getUuid() + "\","
                + "\"aid\":\"0\","
                + "\"net\":\"ws\","
                + "\"type\":\"none\","
                + "\"host\":\"" + argoDomain + "\","
                + "\"path\":\"/?ed=2560\","
                + "\"tls\":\"tls\","
                + "\"sni\":\"" + argoDomain + "\"}";
        String vmessBase64 = java.util.Base64.getEncoder()
                .encodeToString(vmessJson.getBytes(StandardCharsets.UTF_8));
        String wsLink = String.format(WS_URL, vmessBase64);

        List<String> lines = new ArrayList<>();
        if (Files.exists(NODE_FILE_PATH)) {
            lines = new ArrayList<>(Files.readAllLines(NODE_FILE_PATH, StandardCharsets.UTF_8));
        }

        // 查找并替换 ws-argo 链接
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("ws-argo")) {
                lines.set(i, wsLink);
                found = true;
                break;
            }
        }
        if (!found) {
            lines.add(0, wsLink);
        }

        Files.write(NODE_FILE_PATH, lines, StandardCharsets.UTF_8);
    }

    @Override
    public String getAppName() {
        return APP_NAME;
    }
}
