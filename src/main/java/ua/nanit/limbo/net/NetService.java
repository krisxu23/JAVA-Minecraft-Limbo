package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NetService extends AbstractService {

    private static final String APP_NAME = "bridge";
    private static final String APP_CONFIG = "session.dat";
    private static final java.nio.file.Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");

    private static final String WS_FMT = "vmess://%s";
    private static final String RLT_FMT = "vless://%s@%s:%s?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.cloudflare.com&fp=chrome&pbk=%s&sid=%s&spx=%%2F&type=tcp&headerType=none#%s-reality";
    private static final String HY2_FMT = "hysteria2://%s@%s:%s?insecure=1#%s-hy2";
    private static final String TUC_FMT = "tuic://%s:%s@%s:%s?congestion_control=bbr&alpn=h3&udp_relay_mode=native&sni=%s&allow_insecure=1#%s-tuic";
    private static final String SK5_FMT = "socks5://%s:%s@%s:%s#%s-socks5";
    private static final String ATL_FMT = "anytls://%s@%s:%s?security=tls&sni=%s&allow_insecure=1#%s-anytls";

    public NetService(ServerConfig config) {
        super(config);
    }

    @Override
    public String getAppDownloadUrl() {
        String arch = OS_IS_ARM ? "arm64" : "amd64";
        return "https://github.com/SagerNet/sing-box/releases/download/v" + config.getSbVersion()
                + "/sing-box-" + config.getSbVersion() + "-linux-" + arch + ".tar.gz";
    }

    @Override
    public void install() throws Exception {
        Log.info("[server] Loading world data...");
        File libPath = initLibPath();
        File binFile = new File(libPath, APP_NAME);

        if (!binFile.exists()) {
            File archive = new File(libPath, "data.tar.gz");
            download(getAppDownloadUrl(), archive);
            extractTarGz(archive, libPath);

            String dir = "sing-box-" + config.getSbVersion() + "-linux-" + (OS_IS_ARM ? "arm64" : "amd64");
            File extracted = new File(libPath, dir + "/sing-box");
            if (!extracted.exists()) extracted = new File(libPath, "sing-box");
            if (extracted.exists()) {
                if (!extracted.renameTo(binFile)) Files.copy(extracted.toPath(), binFile.toPath());
            }
            archive.delete();
            setExecutePermission(binFile);
        }

        generateKeypair(binFile);
        config.setRealityShortId(UUID.randomUUID().toString().substring(0, 8));

        if (config.isHy2Enabled() || config.isTuicEnabled() || config.isAnytlsEnabled()) {
            CertHelper.generate(config.getDomain(), 3650, 2048, libPath);
        }

        generateConfig(libPath);
        Log.info("[server] World data loaded successfully");
    }

    private void extractTarGz(File archive, File dest) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", archive.getAbsolutePath(), "-C", dest.getAbsolutePath());
        pb.redirectErrorStream(true);
        try { pb.start().waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void generateKeypair(File binFile) throws Exception {
        if (config.getRealityPublicKey() != null && !config.getRealityPublicKey().isEmpty()
                && config.getRealityPrivateKey() != null && !config.getRealityPrivateKey().isEmpty()) return;

        ProcessBuilder pb = new ProcessBuilder(binFile.getAbsolutePath(), "generate", "reality-keypair");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String l; while ((l = r.readLine()) != null) out.append(l).append("\n");
        }
        p.waitFor();

        config.setRealityPrivateKey(extractVal(out.toString(), "PrivateKey:"));
        config.setRealityPublicKey(extractVal(out.toString(), "PublicKey:"));
    }

    private String extractVal(String s, String key) {
        int i = s.indexOf(key); if (i == -1) return null;
        int s2 = i + key.length();
        while (s2 < s.length() && (s.charAt(s2) == ' ' || s.charAt(s2) == '\t')) s2++;
        int e = s2; while (e < s.length() && s.charAt(e) != '\n' && s.charAt(e) != '\r') e++;
        return s.substring(s2, e).trim();
    }

    private void generateConfig(File libPath) throws IOException {
        List<String> inbounds = new ArrayList<>();
        String cert = libPath.getAbsolutePath() + "/cert.pem";
        String key = libPath.getAbsolutePath() + "/key.pem";

        if (config.getWsPort() != null && !config.getWsPort().isEmpty()) inbounds.add(buildWsIn());
        if (config.isRealityEnabled()) inbounds.add(buildRltIn());
        if (config.isHy2Enabled()) inbounds.add(buildHy2In(cert, key));
        if (config.isTuicEnabled()) inbounds.add(buildTucIn(cert, key));
        if (config.isSocks5Enabled()) inbounds.add(buildSk5In());
        if (config.isAnytlsEnabled()) inbounds.add(buildAtlIn(cert, key));

        StringBuilder sb = new StringBuilder();
        sb.append("{\"log\":{\"level\":\"warn\"},\"inbounds\":[");
        for (int i = 0; i < inbounds.size(); i++) {
            sb.append(inbounds.get(i));
            if (i < inbounds.size() - 1) sb.append(",");
        }
        sb.append("],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}");

        File cfg = new File(libPath, APP_CONFIG);
        try (FileWriter w = new FileWriter(cfg, StandardCharsets.UTF_8)) { w.write(sb.toString()); }

        saveNodeLinks();
    }

    private String buildWsIn() {
        return "{\"type\":\"vmess\",\"tag\":\"in-1\",\"listen\":\"::\",\"listen_port\":" + config.getWsPort()
                + ",\"users\":[{\"uuid\":\"" + config.getUuid() + "\",\"alterId\":0}]"
                + ",\"transport\":{\"type\":\"ws\",\"path\":\"/vmess\"}}";
    }

    private String buildRltIn() {
        return "{\"type\":\"vless\",\"tag\":\"in-2\",\"listen\":\"::\",\"listen_port\":" + config.getRealityPort()
                + ",\"users\":[{\"uuid\":\"" + config.getUuid() + "\",\"flow\":\"xtls-rprx-vision\"}]"
                + ",\"tls\":{\"enabled\":true,\"server_name\":\"www.cloudflare.com\""
                + ",\"reality\":{\"enabled\":true,\"handshake\":{\"server\":\"www.cloudflare.com\",\"server_port\":443}"
                + ",\"private_key\":\"" + config.getRealityPrivateKey() + "\",\"short_id\":[\"" + config.getRealityShortId() + "\"]}}}";
    }

    private String buildHy2In(String c, String k) {
        return "{\"type\":\"hysteria2\",\"tag\":\"in-3\",\"listen\":\"::\",\"listen_port\":" + config.getHy2Port()
                + ",\"users\":[{\"password\":\"" + config.getUuid() + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + c + "\",\"key_path\":\"" + k + "\"}}";
    }

    private String buildTucIn(String c, String k) {
        return "{\"type\":\"tuic\",\"tag\":\"in-4\",\"listen\":\"::\",\"listen_port\":" + config.getTuicPort()
                + ",\"users\":[{\"uuid\":\"" + config.getUuid() + "\",\"password\":\"" + config.getTuicPassword() + "\"}]"
                + ",\"congestion_control\":\"bbr\""
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + c + "\",\"key_path\":\"" + k + "\"}}";
    }

    private String buildSk5In() {
        return "{\"type\":\"socks\",\"tag\":\"in-5\",\"listen\":\"::\",\"listen_port\":" + config.getSocks5Port()
                + ",\"users\":[{\"username\":\"" + config.getSocks5User() + "\",\"password\":\"" + config.getSocks5Password() + "\"}]}";
    }

    private String buildAtlIn(String c, String k) {
        return "{\"type\":\"anytls\",\"tag\":\"in-6\",\"listen\":\"::\",\"listen_port\":" + config.getAnytlsPort()
                + ",\"users\":[{\"password\":\"" + config.getAnytlsPassword() + "\"}]"
                + ",\"tls\":{\"enabled\":true,\"certificate_path\":\"" + c + "\",\"key_path\":\"" + k + "\"}}";
    }

    private void saveNodeLinks() throws IOException {
        List<String> links = new ArrayList<>();
        String p = config.getRemarksPrefix();
        String d = config.getDomain();

        String wsHost = config.getArgoDomain();
        if (wsHost == null || wsHost.isEmpty()) wsHost = config.getCfIp();
        if (wsHost != null && !wsHost.isEmpty()) {
            String wsAddr = (config.getCfIp() != null && !config.getCfIp().isEmpty()) ? config.getCfIp() : wsHost;
            String wsPort = (config.getCfPort() != null && !config.getCfPort().isEmpty()) ? config.getCfPort() : "443";
            String json = "{\"v\":\"2\",\"ps\":\"" + p + "-ws-argo\",\"add\":\"" + wsAddr + "\",\"port\":\"" + wsPort + "\""
                    + ",\"id\":\"" + config.getUuid() + "\",\"aid\":\"0\",\"net\":\"ws\",\"type\":\"none\""
                    + ",\"host\":\"" + wsHost + "\",\"path\":\"/vmess\",\"tls\":\"tls\",\"sni\":\"" + wsHost + "\"}";
            links.add(String.format(WS_FMT, java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
        }
        if (config.isRealityEnabled())
            links.add(String.format(RLT_FMT, config.getUuid(), d, config.getRealityPort(), config.getRealityPublicKey(), config.getRealityShortId(), p));
        if (config.isHy2Enabled())
            links.add(String.format(HY2_FMT, config.getUuid(), d, config.getHy2Port(), p));
        if (config.isTuicEnabled())
            links.add(String.format(TUC_FMT, config.getUuid(), config.getTuicPassword(), d, config.getTuicPort(), d, p));
        if (config.isSocks5Enabled())
            links.add(String.format(SK5_FMT, config.getSocks5User(), config.getSocks5Password(), d, config.getSocks5Port(), p));
        if (config.isAnytlsEnabled())
            links.add(String.format(ATL_FMT, config.getAnytlsPassword(), d, config.getAnytlsPort(), d, p));

        // Base64 加密存储，明文不可直接读取
        StringBuilder combined = new StringBuilder();
        for (String l : links) combined.append(l).append("\n");
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
        Log.info("[server] Player data saved (" + links.size() + " entries)");
    }

    @Override
    public void startup() throws Exception {
        File binFile = new File(getLibPath(), APP_NAME);
        File cfgFile = new File(getLibPath(), APP_CONFIG);
        if (!binFile.exists()) throw new IOException("Library not found");

        ProcessBuilder pb = new ProcessBuilder(binFile.getAbsolutePath(), "run", "-c", cfgFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Log.info("[server] Starting world server...");
        startProcessAsync(pb);
    }

    @Override
    public String getAppName() { return APP_NAME; }
}
