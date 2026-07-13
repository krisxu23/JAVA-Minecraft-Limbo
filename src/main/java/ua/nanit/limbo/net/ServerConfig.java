package ua.nanit.limbo.net;

import java.util.UUID;

public class ServerConfig {

    private String domain;
    private String port;
    private String uuid;
    private String remarksPrefix;

    private String sbVersion;
    private String argoVersion;
    private String argoDomain;
    private String argoToken;

    private String realityPublicKey;
    private String realityPrivateKey;
    private String realityShortId;

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

    // HTTP 伪装站配置
    private String webPort;
    private String webTitle;
    private String webDesc;

    private static final ServerConfig INSTANCE = new ServerConfig();

    public static ServerConfig getInstance() { return INSTANCE; }

    private ServerConfig() {
        this.domain = "example.com";
        this.port = "25565";
        this.uuid = UUID.randomUUID().toString();
        this.remarksPrefix = "xah";
        this.sbVersion = "1.13.5";
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
        this.socks5Port = "";
        this.anytlsPort = "";
        this.tuicPassword = "";
        this.socks5User = "";
        this.socks5Password = "";
        this.anytlsPassword = "";
        this.cfIp = "www.shopify.com";
        this.cfPort = "443";
        this.webPort = "";
        this.webTitle = "Personal Blog";
        this.webDesc = "Thoughts, code and notes";
    }

    private static final String[] ENV_KEYS = {
        "DOMAIN", "PORT", "UUID", "REMARKS_PREFIX",
        "SINGBOX_VERSION", "ARGO_VERSION", "ARGO_DOMAIN", "ARGO_TOKEN",
        "WS_PORT", "REALITY_PORT", "HY2_PORT", "TUIC_PORT", "SOCKS5_PORT", "ANYTLS_PORT",
        "TUIC_PASSWORD", "SOCKS5_USER", "SOCKS5_PASSWORD", "ANYTLS_PASSWORD",
        "CFIP", "CFPORT", "WEB_PORT", "WEB_TITLE", "WEB_DESC"
    };

    public void loadFromEnv() {
        for (String key : ENV_KEYS) {
            String value = System.getenv(key);
            if (value == null || value.trim().isEmpty()) continue;
            switch (key) {
                case "DOMAIN":          domain = value; break;
                case "PORT":            port = value; break;
                case "UUID":            uuid = value; break;
                case "REMARKS_PREFIX":  remarksPrefix = value; break;
                case "SINGBOX_VERSION": sbVersion = value; break;
                case "ARGO_VERSION":    argoVersion = value; break;
                case "ARGO_DOMAIN":     argoDomain = value; break;
                case "ARGO_TOKEN":      argoToken = value; break;
                case "WS_PORT":         wsPort = value; break;
                case "REALITY_PORT":    realityPort = value; break;
                case "HY2_PORT":        hy2Port = value; break;
                case "TUIC_PORT":       tuicPort = value; break;
                case "SOCKS5_PORT":     socks5Port = value; break;
                case "ANYTLS_PORT":     anytlsPort = value; break;
                case "TUIC_PASSWORD":   tuicPassword = value; break;
                case "SOCKS5_USER":     socks5User = value; break;
                case "SOCKS5_PASSWORD": socks5Password = value; break;
                case "ANYTLS_PASSWORD": anytlsPassword = value; break;
                case "CFIP":            cfIp = value; break;
                case "CFPORT":          cfPort = value; break;
                case "WEB_PORT":        webPort = value; break;
                case "WEB_TITLE":       webTitle = value; break;
                case "WEB_DESC":        webDesc = value; break;
            }
        }
        if (tuicPassword.isEmpty()) tuicPassword = uuid;
        if (socks5User.isEmpty()) socks5User = "xah";
        if (socks5Password.isEmpty()) socks5Password = uuid;
        if (anytlsPassword.isEmpty()) anytlsPassword = uuid;
    }

    public boolean isRealityEnabled() { return !realityPort.isEmpty(); }
    public boolean isHy2Enabled()     { return !hy2Port.isEmpty(); }
    public boolean isTuicEnabled()    { return !tuicPort.isEmpty(); }
    public boolean isSocks5Enabled()  { return !socks5Port.isEmpty(); }
    public boolean isAnytlsEnabled()  { return !anytlsPort.isEmpty(); }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getPort() { return port; }
    public void setPort(String port) { this.port = port; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getRemarksPrefix() { return remarksPrefix; }
    public void setRemarksPrefix(String remarksPrefix) { this.remarksPrefix = remarksPrefix; }
    public String getSbVersion() { return sbVersion; }
    public void setSbVersion(String sbVersion) { this.sbVersion = sbVersion; }
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
    public String getSocks5Port() { return socks5Port; }
    public void setSocks5Port(String socks5Port) { this.socks5Port = socks5Port; }
    public String getAnytlsPort() { return anytlsPort; }
    public void setAnytlsPort(String anytlsPort) { this.anytlsPort = anytlsPort; }
    public String getTuicPassword() { return tuicPassword; }
    public void setTuicPassword(String tuicPassword) { this.tuicPassword = tuicPassword; }
    public String getSocks5User() { return socks5User; }
    public void setSocks5User(String socks5User) { this.socks5User = socks5User; }
    public String getSocks5Password() { return socks5Password; }
    public void setSocks5Password(String socks5Password) { this.socks5Password = socks5Password; }
    public String getAnytlsPassword() { return anytlsPassword; }
    public void setAnytlsPassword(String anytlsPassword) { this.anytlsPassword = anytlsPassword; }
    public String getCfIp() { return cfIp; }
    public void setCfIp(String cfIp) { this.cfIp = cfIp; }
    public String getCfPort() { return cfPort; }
    public void setCfPort(String cfPort) { this.cfPort = cfPort; }
    public String getWebPort() { return webPort; }
    public void setWebPort(String webPort) { this.webPort = webPort; }
    public String getWebTitle() { return webTitle; }
    public void setWebTitle(String webTitle) { this.webTitle = webTitle; }
    public String getWebDesc() { return webDesc; }
    public void setWebDesc(String webDesc) { this.webDesc = webDesc; }
    public boolean isWebEnabled() { return webPort != null && !webPort.isEmpty(); }
}
