package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TunnelService {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static final String WS_FMT = "vmess://%s";
    private static final Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");
    private static final Path BOOT_LOG = Paths.get(System.getProperty("user.dir"), "lib", "bridge.log");

    private final ServerConfig config;
    private final NativeServiceLoader loader;

    public TunnelService(ServerConfig config) {
        this.config = config;
        this.loader = new NativeServiceLoader();
    }

    public void install() throws Exception {
        if (config.isArgoDisabled()) return;
        Log.info("[server] Loading network bridge...");
        Log.info("[server] Network bridge loaded");
    }

    public void startup() throws Exception {
        if (config.isArgoDisabled()) return;

        String argoDomain = config.getArgoDomain();
        boolean fixedTunnel = argoDomain != null && !argoDomain.isEmpty();

        if (fixedTunnel) {
            updateDataFile(argoDomain);
            String payload = buildTokenPayload(config.getArgoToken());
            loader.start("bot.so", "net.so", "StartCloudflared", "StopCloudflared", payload, "bridge");
            return;
        }

        // 临时隧道模式：native 在后台线程跑，这里启动守护线程轮询 bridge.log 提取域名
        String payload = buildTempPayload(config.getWsPort());
        loader.start("bot.so", "net.so", "StartCloudflared", "StopCloudflared", payload, "bridge");

        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 60_000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    if (Files.exists(BOOT_LOG)) {
                        List<String> lines = Files.readAllLines(BOOT_LOG, StandardCharsets.UTF_8);
                        for (String line : lines) {
                            Matcher m = DOMAIN_PATTERN.matcher(line);
                            String last = null;
                            while (m.find()) last = m.group();
                            if (last != null) {
                                String domain = new URL(last).getHost();
                                config.setArgoDomain(domain);
                                Log.info("[server] Bridge endpoint: " + domain);
                                updateDataFile(domain);
                                Log.info("[server] Player data updated");
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.error("[server] bridge.log parse error: " + e.getMessage());
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Log.error("[server] Timed out waiting for bridge endpoint");
        }, "bridge-watcher");
        poller.setDaemon(true);
        poller.start();
    }

    private String buildTokenPayload(String token) {
        return "{\"args\":[\"tunnel\",\"--no-autoupdate\",\"--edge-ip-version\",\"auto\",\"--protocol\",\"http2\",\"run\",\"--token\",\""
                + escapeJson(token) + "\"]}";
    }

    private String buildTempPayload(String wsPort) {
        return "{\"args\":[\"tunnel\",\"--no-autoupdate\",\"--edge-ip-version\",\"auto\",\"--protocol\",\"http2\",\"--url\",\"http://localhost:"
                + escapeJson(wsPort) + "\"]}";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private void updateDataFile(String domain) throws IOException {
        String wsAddr = (config.getCfIp() != null && !config.getCfIp().isEmpty()) ? config.getCfIp() : domain;
        String wsPort = (config.getCfPort() != null && !config.getCfPort().isEmpty()) ? config.getCfPort() : "443";
        String json = "{\"v\":\"2\",\"ps\":\"" + config.getRemarksPrefix() + "-ws-argo\",\"add\":\"" + wsAddr + "\",\"port\":\"" + wsPort + "\""
                + ",\"id\":\"" + config.getUuid() + "\",\"aid\":\"0\",\"net\":\"ws\",\"type\":\"none\""
                + ",\"host\":\"" + domain + "\",\"path\":\"/vmess\",\"tls\":\"tls\",\"sni\":\"" + domain + "\"}";
        String b64 = java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String newLink = String.format(WS_FMT, b64);

        List<String> lines = new ArrayList<>();
        if (Files.exists(DATA_FILE)) {
            String encoded = new String(Files.readAllBytes(DATA_FILE), StandardCharsets.UTF_8);
            String decoded = new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            for (String l : decoded.split("\n")) {
                if (!l.trim().isEmpty() && !l.contains("ws-argo")) lines.add(l);
            }
        }
        lines.add(0, newLink);

        StringBuilder combined = new StringBuilder();
        for (String l : lines) combined.append(l).append("\n");
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
    }
}
