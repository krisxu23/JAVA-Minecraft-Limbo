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

    /** 在此区域填写你的配置 ↓↓↓ */

    private ServerConfig() {
        this.uuid = "5c002620-79a3-4417-bc96-86490f2c2fbd"; // 客户端 UUID（可用 UUID 在线生成器替换）
        this.domain = "";              // 服务器域名或IP（留空自动获取公网IP）
        this.port = "25565";           // Minecraft 服务器端口
        this.remarksPrefix = "Rustix";    // 节点备注前缀
        this.wsPort = "8001";          // VMess+WebSocket 端口（内部 Argo）
        this.realityPort = "33959";         // VLESS+Reality 端口（TCP）
        this.hy2Port = "33959";             // Hysteria2 端口（UDP）
        this.tuicPort = "38919";            // Tuic 端口（UDP）
        this.socks5Port = "38919";          // Socks5 端口（TCP）
        this.anytlsPort = "";          // AnyTLS 端口（TCP）
        this.cfIp = "www.wto.org"; // Cloudflare 优选 IP
        this.cfPort = "443";           // Cloudflare 优选端口
        this.argoDomain = "ruxtis.5566248.cc.cd";          // Argo 隧道固定域名（留空用临时隧道）
        this.argoToken = "eyJhIjoiN2ZiY2U5ZDc0OGM0MjU5OGZiZjkyYTM5ZjY5MDZkYmIiLCJ0IjoiMDA2MzI4OGYtOGU5Ni00MzhlLWI3ZWQtNzRiN2U4MmRlNDNhIiwicyI6Ik9HSTBaVGsxT0RJdE56VmpPUzAwTVdReUxXSm1PREF0WkRFM1pXUmpORE01TldKaiJ9";           // Argo Tunnel Token（固定隧道必填）
        this.disableArgo = false;      // 禁用 Argo Tunnel
        this.webPort = "";             // HTTP 伪装博客端口（留空禁用）
        this.webTitle = "Personal Blog";
        this.webDesc = "Thoughts, code and notes";
        this.subPort = "";             // 订阅端口
        this.subPath = "sub";          // 订阅路径
        this.nezhaServer = "";         // 哪吒监控域名（留空禁用）
        this.nezhaPort = "";           // 哪吒监控端口（留空使用 v1 模式）
        this.nezhaKey = "";            // 哪吒监控 Key
        this.tgChatId = "";            // Telegram 通知 Chat ID（留空禁用）
        this.tgBotToken = "";          // Telegram Bot Token
        this.autoAccess = false;       // 是否启用 AutoAccess
        this.projectUrl = "";          // 项目 URL
        this.uploadUrl = "";           // 上传 URL
        this.ytWarpOut = false;        // 是否启用 YouTube Warp
        this.sbVersion = "1.13.14";    // sing-box 版本
        this.sbDownloadUrl = "";       // sing-box 下载地址（留空自动）
        this.cfDownloadUrl = "";       // cloudflared 下载地址（留空自动）

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
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5)).build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET().header("User-Agent", "curl/8.0").build();
                java.net.http.HttpResponse<String> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    String ip = resp.body().trim();
                    if (ip.matches("^[0-9a-fA-F.:]+$") && ip.length() >= 7) return ip;
                }
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
    public boolean isDisableArgo()  { return disableArgo; }
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
