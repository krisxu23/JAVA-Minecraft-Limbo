package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内置自保活：用 Java 定时器周期性访问 projectUrl，防止容器因空闲被平台休眠。
 *
 * 不依赖任何第三方保活服务，完全自主可控。
 * 访问间隔：每 5 分钟一次。
 * 访问失败仅打 warn 日志，不阻断主流程，下次继续重试。
 */
public class KeepAliveService {
    private final ServerConfig config;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final long INTERVAL_MINUTES = 5;
    private ScheduledExecutorService scheduler;

    public KeepAliveService(ServerConfig config) {
        this.config = config;
    }

    /**
     * 启动自保活定时任务。
     * 若未启用（autoAccess=false 或 projectUrl 为空）则直接返回。
     */
    public void register() {
        if (!config.isAutoAccessEnabled()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "keepalive-thread");
            t.setDaemon(true);
            return t;
        });
        // 立即访问一次，然后每 5 分钟访问一次
        scheduler.scheduleAtFixedRate(this::ping, 0, INTERVAL_MINUTES, TimeUnit.MINUTES);
        Log.info("[heartbeat] Started, interval %d minutes", INTERVAL_MINUTES);
    }

    /**
     * 访问一次 projectUrl。
     */
    private void ping() {
        try {
            String url = config.getProjectUrl();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Log.info("[heartbeat] OK");
            } else {
                Log.warn("[heartbeat] HTTP %d", resp.statusCode());
            }
        } catch (Exception e) {
            Log.warn("[heartbeat] Error: %s", e.getMessage());
        }
    }
}
