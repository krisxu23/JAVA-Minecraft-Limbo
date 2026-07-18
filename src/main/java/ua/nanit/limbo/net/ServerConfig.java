package ua.nanit.limbo.net;

import java.util.UUID;

public class ServerConfig {

    private String domain;
    private String port;
    private String uuid;
    private String remarksPrefix;

    private String wsPort;
    private String realityPort;
    private String hy2Port;
    private String tuicPort;
    private String socks5Port;
    private String anytlsPort;

    private String tuicPassword;
    private String socks5User;
    private String socks5Password;
    private String anytlsPassword;

    private String cfIp;
    private String cfPort;

    private String sbVersion;
    private String sbDownloadUrl;
    private String cfDownloadUrl;

    private String argoDomain;
    private String argoToken;
    private boolean disableArgo;

    private String realityPrivateKey;
    private String realityPublicKey;
    private String realityShortId;

    private String webPort;
    private String webTitle;
    private String webDesc;

    private String subPort;
    private String subPath;

    private String nezhaServer;
    private String nezhaPort;
    private String nezhaKey;

    private String tgChatId;
    private String tgBotToken;

    private boolean autoAccess;
    private String projectUrl;

    private String uploadUrl;
    private boolean ytWarpOut;

    private static final ServerConfig INSTANCE = new ServerConfig();

    public static ServerConfig getInstance() { return INSTANCE; }

    /** 读取环境变量，未设置时返回空字符串 */
    private static String env(String key) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : "";
    }

    /** 读取环境变量，未设置时使用传入的默认值 */
    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    // ======================== 环境变量定义 ========================
    // 代码默认值会被 Pterodactyl 面板 Variables 中同名环境变量覆盖。
    // 以下标有默认值的字段程序会自动沿用；留空的字段程序自动跳过/禁用。
    // ==============================================================
    private ServerConfig() {
        this.uuid = env("UUID", "2523c510-9ff0-415b-9582-93949bfae7e3"); // 节点 UUID，建议自行生成
        this.domain = env("DOMAIN");                 // 服务器域名或 IP，留空自动获取公网 IP
        this.port = env("PORT", "25565");            // Minecraft 伪装服务器端口
        this.remarksPrefix = env("NAME");            // 节点名称前缀
        this.wsPort = env("ARGO_PORT", "8001");      // Argo 隧道端口，需与 Cloudflare 设置一致
        this.realityPort = env("REALITY_PORT");      // VLESS+Reality 端口（TCP），默认不启用
        this.hy2Port = env("HY2_PORT");              // Hysteria2 端口（UDP），默认不启用
        this.tuicPort = env("TUIC_PORT");            // TUIC 端口（UDP），默认不启用
        this.socks5Port = env("S5_PORT");            // SOCKS5 端口（TCP），默认不启用
        this.anytlsPort = env("ANYTLS_PORT");        // AnyTLS 端口（TCP），默认不启用
        this.cfIp = env("CFIP", "spring.io");        // Argo 优选域名或优选 IP
        this.cfPort = env("CFPORT", "443");          // Argo 优选端口
        this.argoDomain = env("ARGO_DOMAIN");        // 固定隧道域名，留空使用临时隧道
        this.argoToken = env("ARGO_AUTH");           // 固定隧道 token/json，留空使用临时隧道
        this.disableArgo = "true".equalsIgnoreCase(env("DISABLE_ARGO", "false")); // true=禁用 Argo 隧道
        this.webPort = env("WEB_PORT");              // HTTP 伪装博客端口，默认不启用
        this.webTitle = env("WEB_TITLE", "Personal Blog");
        this.webDesc = env("WEB_DESC", "Thoughts, code and notes");
        this.subPort = env("SUB_PORT");              // 订阅端口
        this.subPath = env("SUB_PATH", "sub");       // 获取订阅的路径
        this.nezhaServer = env("NEZHA_SERVER");      // 哪吒面板地址，v1 格式: nezha.xxx.com:8008
        this.nezhaPort = env("NEZHA_PORT");          // 哪吒 v0 agent 端口，v1 请留空
        this.nezhaKey = env("NEZHA_KEY");            // 哪吒 v1 的 NZ_CLIENT_SECRET / v0 agent 密钥
        this.tgChatId = env("CHAT_ID");              // Telegram 机器人 Chat ID
        this.tgBotToken = env("BOT_TOKEN");          // Telegram 机器人 Token
        this.autoAccess = "true".equalsIgnoreCase(env("AUTO_ACCESS", "false")); // true=开启自动保活
        this.projectUrl = env("PROJECT_URL");        // 项目地址，开启自动保活或上传订阅时需填写
        this.uploadUrl = env("UPLOAD_URL");          // 节点/订阅自动上传到订阅器的地址
        this.ytWarpOut = "true".equalsIgnoreCase(env("YT_WARPOUT", "false"));   // true=YouTube 走 WARP 出站
        this.sbVersion = env("SB_VERSION", "1.13.14");
        this.sbDownloadUrl = env("SB_DOWNLOAD_URL"); // sing-box 下载地址，留空自动
        this.cfDownloadUrl = env("CF_DOWNLOAD_URL"); // cloudflared 下载地址，留空自动
        // ==============================================================

        if (domain.isEmpty()) domain = fetchPublicIp();
        if (tuicPassword == null || tuicPassword.isEmpty()) tuicPassword = uuid;
        if (socks5User == null || socks5User.isEmpty()) socks5User = "xah";
        if (socks5Password == null || socks5Password.isEmpty()) socks5Password = uuid;
        if (anytlsPassword == null || anytlsPassword.isEmpty()) anytlsPassword = uuid;
    }

    /** 在此区域填写你的配置 ↑↑↑ */

    private String fetchPublicIp() {
        String[] services = {
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "https://icanhazip.com"
        };
        for (String url : services) {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "curl/8.0");
                int code = conn.getResponseCode();
                if (code == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String ip = reader.readLine();
                    reader.close();
                    if (ip != null && ip.matches("^[0-9a-fA-F.:]+$") && ip.length() >= 7) return ip.trim();
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        return "";
    }

    // --- Feature checks ---
    public boolean hasProxyServices() {
        return notEmpty(realityPort) || notEmpty(hy2Port) || notEmpty(tuicPort)
            || notEmpty(socks5Port) || notEmpty(anytlsPort);
    }
    public boolean isRealityEnabled() { return notEmpty(realityPort); }
    public boolean isHy2Enabled()     { return notEmpty(hy2Port); }
    public boolean isTuicEnabled()    { return notEmpty(tuicPort); }
    public boolean isSocks5Enabled()  { return notEmpty(socks5Port); }
    public boolean isAnytlsEnabled()  { return notEmpty(anytlsPort); }
    public boolean isWebEnabled()     { return notEmpty(webPort); }
    public boolean isSubEnabled()     { return notEmpty(subPort); }
    public boolean isNezhaEnabled()   { return notEmpty(nezhaServer) && notEmpty(nezhaKey); }
    public boolean isNezhaV1()        { return nezhaPort == null || nezhaPort.isEmpty(); }
    public boolean isTgEnabled()      { return notEmpty(tgChatId) && notEmpty(tgBotToken); }
    public boolean isAutoAccessEnabled() { return autoAccess && notEmpty(projectUrl); }
    public boolean isUploadEnabled()  { return notEmpty(uploadUrl); }
    private boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    // --- Getters ---
    public String getDomain()       { return domain; }
    public String getPort()         { return port; }
    public String getUuid()         { return uuid; }
    public String getRemarksPrefix(){ return remarksPrefix; }
    public String getWsPort()       { return wsPort; }
    public String getRealityPort()  { return realityPort; }
    public String getHy2Port()      { return hy2Port; }
    public String getTuicPort()     { return tuicPort; }
    public String getSocks5Port()   { return socks5Port; }
    public String getAnytlsPort()   { return anytlsPort; }
    public String getTuicPassword()   { return tuicPassword; }
    public String getSocks5User()     { return socks5User; }
    public String getSocks5Password() { return socks5Password; }
    public String getAnytlsPassword() { return anytlsPassword; }
    public String getCfIp()         { return cfIp; }
    public String getCfPort()       { return cfPort; }
    public String getSbVersion()    { return sbVersion; }
    public String getSbDownloadUrl(){ return sbDownloadUrl; }
    public String getCfDownloadUrl(){ return cfDownloadUrl; }
    public String getArgoDomain()   { return argoDomain; }
    public void setArgoDomain(String v) { this.argoDomain = v; }
    public String getArgoToken()    { return argoToken; }
    public boolean isArgoDisabled() { return disableArgo; }
    public String getWebPort()      { return webPort; }
    public String getWebTitle()     { return webTitle; }
    public String getWebDesc()      { return webDesc; }
    public String getSubPort()      { return subPort; }
    public String getSubPath()      { return subPath; }
    public String getNezhaServer()  { return nezhaServer; }
    public String getNezhaPort()    { return nezhaPort; }
    public String getNezhaKey()     { return nezhaKey; }
    public String getTgChatId()     { return tgChatId; }
    public String getTgBotToken()   { return tgBotToken; }
    public boolean isAutoAccess()   { return autoAccess; }
    public String getProjectUrl()   { return projectUrl; }
    public String getUploadUrl()    { return uploadUrl; }
    public boolean isYtWarpOut()    { return ytWarpOut; }

    public String getRealityPrivateKey() { return realityPrivateKey; }
    public void setRealityPrivateKey(String v) { this.realityPrivateKey = v; }
    public String getRealityPublicKey() { return realityPublicKey; }
    public void setRealityPublicKey(String v) { this.realityPublicKey = v; }
    public String getRealityShortId() { return realityShortId; }
    public void setRealityShortId(String v) { this.realityShortId = v; }
}
