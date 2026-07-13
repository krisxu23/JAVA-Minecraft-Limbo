package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

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
        netService.startup();
        Thread.sleep(3000);
        tunnelService.startup();
        httpService.startup();
        Log.info("[server] All services started");
    }

    public ServerConfig getConfig() { return config; }
}
