package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 向外部保活服务注册 PROJECT_URL，让对方定时访问防止容器休眠。
 *
 * 保活服务地址：https://oooo.serv00.net/add-url（与 sbx-native 使用的同一服务）。
 * 注册失败仅打 warn 日志，不阻断主流程。
 */
public class KeepAliveService {
    private final ServerConfig config;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String KEEPALIVE_URL = "https://oooo.serv00.net/add-url";

    public KeepAliveService(ServerConfig config) {
        this.config = config;
    }

    /**
     * 向保活服务注册当前项目公网 URL。
     * 若未启用（autoAccess=false 或 projectUrl 为空）则直接返回。
     */
    public void register() {
        if (!config.isAutoAccessEnabled()) return;
        try {
            // form-urlencoded 提交项目 URL
            String body = "url=" + URLEncoder.encode(config.getProjectUrl(), StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(KEEPALIVE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Log.info("[keepalive] Registered to keepalive service: %s", config.getProjectUrl());
            } else {
                Log.warn("[keepalive] Register failed: HTTP %d", resp.statusCode());
            }
        } catch (Exception e) {
            Log.warn("[keepalive] Register error: %s", e.getMessage());
        }
    }
}
