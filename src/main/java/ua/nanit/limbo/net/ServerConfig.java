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

    /** 从环境变量读取配置，未设置时使用代码默认值 */
    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }

    /** 在此区域填写你的配置 ↓↓↓
     *  这些是代码默认值，会被同名环境变量覆盖。
     *  Pterodactyl 面板 Variables 中设置的变量会作为环境变量传入。 */

    private ServerConfig() {
        this.uuid = env("UUID", "2523c510-9ff0-415b-9582-93949bfae7e3");
        this.domain = "";              // 留空自动获取公网IP
        this.port = env("PORT", "25565");
        this.remarksPrefix = env("NAME", "votexa");
        this.wsPort = env("ARGO_PORT", "8001");
        this.realityPort = env("REALITY_PORT", "25921");
        this.hy2Port = env("HY2_PORT", "25921");
        this.tuicPort = env("TUIC_PORT", "");
        this.socks5Port = env("S5_PORT", "");
        this.anytlsPort = env("ANYTLS_PORT", "");
        this.cfIp = env("CFIP", "www.wto.org");
        this.cfPort = env("CFPORT", "443");
        this.argoDomain = env("ARGO_DOMAIN", "votexa.5566248.cc.cd");
        this.argoToken = env("ARGO_AUTH", "");
        this.disableArgo = "true".equalsIgnoreCase(env("DISABLE_ARGO", "false"));
        this.webPort = env("WEB_PORT", "");
        this.webTitle = env("WEB_TITLE", "Personal Blog");
        this.webDesc = env("WEB_DESC", "Thoughts, code and notes");
        this.subPort = env("SUB_PORT", "");
        this.subPath = env("SUB_PATH", "sub");
        this.nezhaServer = env("NEZHA_SERVER", "");
        this.nezhaPort = env("NEZHA_PORT", "");
        this.nezhaKey = env("NEZHA_KEY", "");
        this.tgChatId = env("CHAT_ID", "");
        this.tgBotToken = env("BOT_TOKEN", "");
        this.autoAccess = "true".equalsIgnoreCase(env("AUTO_ACCESS", "false"));
        this.projectUrl = env("PROJECT_URL", "");
        this.uploadUrl = env("UPLOAD_URL", "");
        this.ytWarpOut = "true".equalsIgnoreCase(env("YT_WARPOUT", "false"));
        this.sbVersion = env("SB_VERSION", "1.13.14");
        this.sbDownloadUrl = env("SB_DOWNLOAD_URL", "");
        this.cfDownloadUrl = env("CF_DOWNLOAD_URL", "");

        // argoToken 为空时使用默认的固定隧道 token
        if (this.argoToken.isEmpty()) {
            this.argoToken = "eyJhIjoiN2ZiY2U5ZDc0OGM0MjU5OGZiZjkyYTM5ZjY5MDZkYmIiLCJ0IjoiZWM4Y2E2MjAtOTc2My00NjQzLWE2MWItMWJhYzU5MTNhNzhmIiwicyI6IllqazBOamhtWldJdFkyRmtaQzAwTjJGbUxXRXpNVEl0WW1WaU56VmlPVEkzT1RCbCJ9";
        }

        if (domain == null || domain.isEmpty()) {
            domain = fetchPublicIp();
        }
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
