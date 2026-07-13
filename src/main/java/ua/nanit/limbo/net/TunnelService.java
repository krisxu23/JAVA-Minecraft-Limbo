package ua.nanit.limbo.net;

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

public class TunnelService extends AbstractService {

    private static final String APP_NAME = "tunnel";
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static final String WS_FMT = "vmess://%s";
    private static final java.nio.file.Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");

    public TunnelService(ServerConfig config) {
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
        Log.info("[server] Loading network bridge...");
        File libPath = initLibPath();
        File binFile = new File(libPath, APP_NAME);

        if (!binFile.exists()) {
            download(getAppDownloadUrl(), binFile);
            setExecutePermission(binFile);
        }
        Log.info("[server] Network bridge loaded");
    }

    @Override
    public void startup() throws Exception {
        File binFile = new File(getLibPath(), APP_NAME);
        if (!binFile.exists()) throw new IOException("Bridge library not found");

        if (config.getArgoDomain() != null && !config.getArgoDomain().isEmpty()) {
            updateDataFile(config.getArgoDomain());
        }

        final String token = config.getArgoToken();

        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Process process = null;
                try {
                    Log.info("[server] Establishing network bridge...");
                    ProcessBuilder pb;

                    if (token == null || token.trim().isEmpty()) {
                        pb = new ProcessBuilder(
                                binFile.getAbsolutePath(), "tunnel", "--no-autoupdate",
                                "--edge-ip-version", "auto", "--protocol", "http2",
                                "--url", "http://localhost:" + config.getWsPort());
                    } else {
                        pb = new ProcessBuilder(
                                binFile.getAbsolutePath(), "tunnel", "--no-autoupdate",
                                "--edge-ip-version", "auto", "--protocol", "http2",
                                "run", "--token", token);
                    }
                    pb.redirectErrorStream(true);
                    process = pb.start();

                    final Process fp = process;
                    Thread logThread = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(fp.getInputStream()))) {
                            String line;
                            boolean found = config.getArgoDomain() != null && !config.getArgoDomain().isEmpty();
                            while ((line = r.readLine()) != null) {
                                if (!found) {
                                    Matcher m = DOMAIN_PATTERN.matcher(line);
                                    String last = null;
                                    while (m.find()) last = m.group();
                                    if (last != null) {
                                        found = true;
                                        try {
                                            String domain = new URL(last).getHost();
                                            config.setArgoDomain(domain);
                                            Log.info("[server] Bridge endpoint: " + domain);
                                            updateDataFile(domain);
                                            Log.info("[server] Player data updated");
                                        } catch (Exception e) {
                                            Log.error("[server] Parse error: " + e.getMessage());
                                        }
                                    }
                                }
                                Log.info("[worker] " + line);
                            }
                        } catch (IOException ignored) {}
                    }, "worker-3");
                    logThread.setDaemon(true);
                    logThread.start();

                    int exit = process.waitFor();
                    Log.info("[system] Bridge process restarted");
                    Thread.sleep(3000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (process != null && process.isAlive()) process.destroyForcibly();
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }, "worker-4").start();
    }

    private void updateDataFile(String domain) throws IOException {
        String wsAddr = (config.getCfIp() != null && !config.getCfIp().isEmpty()) ? config.getCfIp() : domain;
        String wsPort = (config.getCfPort() != null && !config.getCfPort().isEmpty()) ? config.getCfPort() : "443";
        String json = "{\"v\":\"2\",\"ps\":\"" + config.getRemarksPrefix() + "-ws-argo\",\"add\":\"" + wsAddr + "\",\"port\":\"" + wsPort + "\""
                + ",\"id\":\"" + config.getUuid() + "\",\"aid\":\"0\",\"net\":\"ws\",\"type\":\"none\""
                + ",\"host\":\"" + domain + "\",\"path\":\"/?ed=2560\",\"tls\":\"tls\",\"sni\":\"" + domain + "\"}";
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

    @Override
    public String getAppName() { return APP_NAME; }
}
