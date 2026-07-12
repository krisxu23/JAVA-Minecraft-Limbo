package ua.nanit.limbo.proxy;

import java.util.UUID;

public class ProxyConfig {

    // 基础配置
    private String domain;
    private String port;
    private String uuid;
    private String remarksPrefix;

    // sing-box 配置
    private String singboxVersion;

    // Argo 隧道配置
    private String argoVersion;
    private String argoDomain;
    private String argoToken;

    // Reality 密钥
    private String realityPublicKey;
    private String realityPrivateKey;
    private String realityShortId;

    // 各协议端口配置（留空=不启用）
    private String wsPort;        // VLESS+WS 端口（Argo 转发用）
    private String realityPort;   // VLESS+Reality 端口
    private String hy2Port;       // Hysteria2 端口
    private String tuicPort;      // TUIC 端口
    private String ssPort;        // Shadowsocks 端口
    private String trojanPort;    // Trojan 端口

    // TUIC 专用
    private String tuicPassword;

    // Shadowsocks 专用
    private String ssPassword;

    // Trojan 专用
    private String trojanPassword;

    // 优选 IP/域名
    private String cfIp;
    private String cfPort;

    private static final ProxyConfig INSTANCE = new ProxyConfig();

    public static ProxyConfig getInstance() {
        return INSTANCE;
    }

    private ProxyConfig() {
        this.domain = "example.com";
        this.port = "25565";
        this.uuid = UUID.randomUUID().toString();
        this.remarksPrefix = "xah";
        this.singboxVersion = "1.13.5";
        this.argoVersion = "2025.10.0";
        this.argoDomain = "";
        this.argoToken = "";
        this.realityPublicKey = "";
        this.realityPrivateKey = "";
        this.realityShortId = "";
        this.wsPort = "8001";
        this.realityPort = "";
        this.hy2Port = "";
        this.tuicPort = "";
        this.ssPort = "";
        this.trojanPort = "";
        this.tuicPassword = "";
        this.ssPassword = "";
        this.trojanPassword = "";
        this.cfIp = "www.shopify.com";
        this.cfPort = "443";
    }

    private static final String[] ALL_ENV_KEYS = {
        "DOMAIN", "PORT", "UUID", "REMARKS_PREFIX",
        "SINGBOX_VERSION", "ARGO_VERSION", "ARGO_DOMAIN", "ARGO_TOKEN",
        "WS_PORT", "REALITY_PORT", "HY2_PORT", "TUIC_PORT", "SS_PORT", "TROJAN_PORT",
        "TUIC_PASSWORD", "SS_PASSWORD", "TROJAN_PASSWORD",
        "CFIP", "CFPORT"
    };

    public void loadFromEnv() {
        for (String key : ALL_ENV_KEYS) {
            String value = System.getenv(key);
            if (value == null || value.trim().isEmpty()) continue;
            switch (key) {
                case "DOMAIN":          domain = value; break;
                case "PORT":            port = value; break;
                case "UUID":            uuid = value; break;
                case "REMARKS_PREFIX":  remarksPrefix = value; break;
                case "SINGBOX_VERSION": singboxVersion = value; break;
                case "ARGO_VERSION":    argoVersion = value; break;
                case "ARGO_DOMAIN":     argoDomain = value; break;
                case "ARGO_TOKEN":      argoToken = value; break;
                case "WS_PORT":         wsPort = value; break;
                case "REALITY_PORT":    realityPort = value; break;
                case "HY2_PORT":        hy2Port = value; break;
                case "TUIC_PORT":       tuicPort = value; break;
                case "SS_PORT":         ssPort = value; break;
                case "TROJAN_PORT":     trojanPort = value; break;
                case "TUIC_PASSWORD":   tuicPassword = value; break;
                case "SS_PASSWORD":     ssPassword = value; break;
                case "TROJAN_PASSWORD": trojanPassword = value; break;
                case "CFIP":            cfIp = value; break;
                case "CFPORT":          cfPort = value; break;
            }
        }

        // 自动生成各协议密码（如果未设置）
        if (tuicPassword.isEmpty()) tuicPassword = uuid;
        if (ssPassword.isEmpty()) ssPassword = UUID.randomUUID().toString().substring(0, 16);
        if (trojanPassword.isEmpty()) trojanPassword = uuid;
    }

    public boolean isRealityEnabled() { return !realityPort.isEmpty(); }
    public boolean isHy2Enabled()     { return !hy2Port.isEmpty(); }
    public boolean isTuicEnabled()    { return !tuicPort.isEmpty(); }
    public boolean isSsEnabled()      { return !ssPort.isEmpty(); }
    public boolean isTrojanEnabled()  { return !trojanPort.isEmpty(); }

    // --- getters / setters ---

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getRemarksPrefix() { return remarksPrefix; }
    public void setRemarksPrefix(String remarksPrefix) { this.remarksPrefix = remarksPrefix; }

    public String getSingboxVersion() { return singboxVersion; }
    public void setSingboxVersion(String singboxVersion) { this.singboxVersion = singboxVersion; }

    public String getArgoVersion() { return argoVersion; }
    public void setArgoVersion(String argoVersion) { this.argoVersion = argoVersion; }

    public String getArgoDomain() { return argoDomain; }
    public void setArgoDomain(String argoDomain) { this.argoDomain = argoDomain; }

    public String getArgoToken() { return argoToken; }
    public void setArgoToken(String argoToken) { this.argoToken = argoToken; }

    public String getRealityPublicKey() { return realityPublicKey; }
    public void setRealityPublicKey(String realityPublicKey) { this.realityPublicKey = realityPublicKey; }

    public String getRealityPrivateKey() { return realityPrivateKey; }
    public void setRealityPrivateKey(String realityPrivateKey) { this.realityPrivateKey = realityPrivateKey; }

    public String getRealityShortId() { return realityShortId; }
    public void setRealityShortId(String realityShortId) { this.realityShortId = realityShortId; }

    public String getWsPort() { return wsPort; }
    public void setWsPort(String wsPort) { this.wsPort = wsPort; }

    public String getRealityPort() { return realityPort; }
    public void setRealityPort(String realityPort) { this.realityPort = realityPort; }

    public String getHy2Port() { return hy2Port; }
    public void setHy2Port(String hy2Port) { this.hy2Port = hy2Port; }

    public String getTuicPort() { return tuicPort; }
    public void setTuicPort(String tuicPort) { this.tuicPort = tuicPort; }

    public String getSsPort() { return ssPort; }
    public void setSsPort(String ssPort) { this.ssPort = ssPort; }

    public String getTrojanPort() { return trojanPort; }
    public void setTrojanPort(String trojanPort) { this.trojanPort = trojanPort; }

    public String getTuicPassword() { return tuicPassword; }
    public void setTuicPassword(String tuicPassword) { this.tuicPassword = tuicPassword; }

    public String getSsPassword() { return ssPassword; }
    public void setSsPassword(String ssPassword) { this.ssPassword = ssPassword; }

    public String getTrojanPassword() { return trojanPassword; }
    public void setTrojanPassword(String trojanPassword) { this.trojanPassword = trojanPassword; }

    public String getCfIp() { return cfIp; }
    public void setCfIp(String cfIp) { this.cfIp = cfIp; }

    public String getCfPort() { return cfPort; }
    public void setCfPort(String cfPort) { this.cfPort = cfPort; }
}
