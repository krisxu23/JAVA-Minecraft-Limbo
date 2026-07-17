package ua.nanit.limbo.net;

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import ua.nanit.limbo.server.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class NetService {

    private static final java.nio.file.Path DATA_FILE = Paths.get(System.getProperty("user.dir"), "players.dat");

    private static final String WS_FMT = "vmess://%s";
    private static final String RLT_FMT = "vless://%s@%s:%s?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.cloudflare.com&fp=chrome&pbk=%s&sid=%s&spx=%%2F&type=tcp&headerType=none#%s-reality";
    private static final String HY2_FMT = "hysteria2://%s@%s:%s?insecure=1#%s-hy2";
    private static final String TUC_FMT = "tuic://%s:%s@%s:%s?congestion_control=bbr&alpn=h3&udp_relay_mode=native&sni=%s&allow_insecure=1#%s-tuic";
    private static final String SK5_FMT = "socks5://%s:%s@%s:%s#%s-socks5";
    private static final String ATL_FMT = "anytls://%s@%s:%s?security=tls&sni=%s&allow_insecure=1#%s-anytls";

    private final ServerConfig config;
    private final NativeServiceLoader loader;
    private NativeServiceLoader.NativeHandle handle;

    public NetService(ServerConfig config) {
        this.config = config;
        this.loader = new NativeServiceLoader();
    }

    /** 供 NezhaService 等共享同一个 loader（复用架构检测、lib 目录） */
    public NativeServiceLoader getLoader() {
        return loader;
    }

    public void install() throws Exception {
        Log.info("[server] Loading world data...");
        File libPath = AbstractService.LIB_PATH;
        if (!libPath.exists() && !libPath.mkdirs()) {
            throw new IOException("Cannot create lib dir: " + libPath);
        }

        generateRealityKeypair();
        config.setRealityShortId(UUID.randomUUID().toString().substring(0, 8));

        if (config.isHy2Enabled() || config.isTuicEnabled() || config.isAnytlsEnabled()) {
            CertHelper.generate(config.getDomain(), 3650, 2048, libPath);
        }

        generateConfig(libPath);
        Log.info("[server] World data loaded successfully");
    }

    private void generateRealityKeypair() {
        File keypairFile = new File(AbstractService.LIB_PATH, "keypair.properties");
        // 先尝试读已有 keypair
        if (keypairFile.exists()) {
            Properties props = new Properties();
            try (Reader r = new FileReader(keypairFile)) {
                props.load(r);
                String priv = props.getProperty("SessionKey");
                String pub = props.getProperty("VerifyKey");
                if (priv != null && !priv.isEmpty() && pub != null && !pub.isEmpty()) {
                    config.setRealityPrivateKey(priv);
                    config.setRealityPublicKey(pub);
                    Log.info("[server] Reusing session keys");
                    return;
                }
            } catch (IOException e) {
                Log.warn("[server] Failed to read session: %s", e.getMessage());
            }
        }
        X25519KeyPairGenerator gen = new X25519KeyPairGenerator();
        gen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        org.bouncycastle.crypto.AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        X25519PrivateKeyParameters priv = (X25519PrivateKeyParameters) pair.getPrivate();
        X25519PublicKeyParameters pub = (X25519PublicKeyParameters) pair.getPublic();
        String privB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getEncoded());
        String pubB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getEncoded());
        config.setRealityPrivateKey(privB64);
        config.setRealityPublicKey(pubB64);
        Properties props = new Properties();
        props.setProperty("SessionKey", privB64);
        props.setProperty("VerifyKey", pubB64);
        try (Writer w = new FileWriter(keypairFile)) {
            props.store(w, "Session keys");
        } catch (IOException e) {
            Log.warn("[server] Failed to save session: %s", e.getMessage());
        }
        Log.info("[server] Generated session keys");
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
        sb.append("{\"log\":{\"level\":\"fatal\"},\"inbounds\":[");
        for (int i = 0; i < inbounds.size(); i++) {
            sb.append(inbounds.get(i));
            if (i < inbounds.size() - 1) sb.append(",");
        }
        sb.append("],\"outbounds\":[{\"type\":\"direct\",\"tag\":\"direct\"}]}");

        File cfg = new File(libPath, "config.json");
        try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(cfg), StandardCharsets.UTF_8)) { w.write(sb.toString()); }

        saveNodeLinks();
    }

    private String buildWsIn() {
        return "{\"type\":\"vmess\",\"tag\":\"in-1\",\"listen\":\"::\",\"listen_port\":" + config.getWsPort()
                + ",\"users\":[{\"uuid\":\"" + config.getUuid() + "\",\"alterId\":0}]"
                + ",\"transport\":{\"type\":\"ws\",\"path\":\"/\",\"max_early_data\":2560,\"early_data_header_name\":\"Sec-WebSocket-Protocol\"}}";
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

        // VMess/Argo 链接由 TunnelService.updateDataFile() 在隧道启动后生成
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
        String encoded = Base64.getEncoder()
                .encodeToString(combined.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(DATA_FILE, encoded.getBytes(StandardCharsets.UTF_8));
        Log.info("[server] Player data saved (" + links.size() + " entries)");
    }

    public void startup() throws Exception {
        File cfgFile = new File(AbstractService.LIB_PATH, "config.json");
        if (!cfgFile.exists()) throw new IOException("Config not found: " + cfgFile);

        // payload JSON: {"config":"<abs path>","workingDir":".","disableColor":true}
        String cfgPath = cfgFile.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\"");
        String payload = "{\"config\":\"" + cfgPath + "\",\"workingDir\":\".\",\"disableColor\":true}";

        Log.info("[server] Starting world server...");
        handle = loader.start("sbx.so", "world.so", "StartSingBox", "StopSingBox", payload, "world-engine", true);
    }

    public void shutdown() {
        if (handle != null) handle.stop();
        Log.info("[server] World server stopped");
    }
}
