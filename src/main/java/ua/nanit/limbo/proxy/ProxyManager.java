package ua.nanit.limbo.proxy;

import ua.nanit.limbo.server.Log;

public class ProxyManager {

    private final ProxyConfig config;
    private final SingBoxService singBoxService;
    private final ArgoService argoService;

    public ProxyManager() {
        this.config = ProxyConfig.getInstance();
        this.singBoxService = new SingBoxService(config);
        this.argoService = new ArgoService(config);
    }

    public void install() throws Exception {
        Log.info("Loading proxy configuration from environment...");
        config.loadFromEnv();

        Log.info("Installing proxy services...");

        Log.info("Installing sing-box service...");
        singBoxService.install();
        Log.info("sing-box service installed");

        Log.info("Installing Argo service...");
        argoService.install();
        Log.info("Argo service installed");

        Log.info("All proxy services installed successfully");
    }

    public void startup() throws Exception {
        Log.info("Starting proxy services...");

        Log.info("Starting sing-box service...");
        singBoxService.startup();
        Thread.sleep(3000);

        Log.info("Starting Argo service...");
        argoService.startup();

        Log.info("All proxy services started");
    }

    public SingBoxService getSingBoxService() {
        return singBoxService;
    }

    public ArgoService getArgoService() {
        return argoService;
    }

    public ProxyConfig getConfig() {
        return config;
    }
}
