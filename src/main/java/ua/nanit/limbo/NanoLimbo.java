package ua.nanit.limbo;

import ua.nanit.limbo.proxy.*;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entry point for the NanoLimbo proxy + Minecraft limbo server.
 * Orchestrates proxy service startup (sing-box, cloudflared) and then
 * starts the Minecraft server. Refactored from a ~758-line god class
 * into a thin orchestrator delegating to focused service classes.
 */
public final class NanoLimbo {

    private static Process sbxProcess;
    private static Process cfProcess;
    private static Map<String, String> env;
    private static ScheduledExecutorService healthMonitor;
    private static final AtomicInteger sbxRestartCount = new AtomicInteger(0);
    private static final AtomicInteger cfRestartCount = new AtomicInteger(0);
    private static final int MAX_RESTART_ATTEMPTS = 5;
    private static final long RESTART_COOLDOWN_MS = 10_000;

    public static void main(String[] args) {

        if (Float.parseFloat(System.getProperty("java.class.version")) < 52.0) {
            System.err.println(ConsoleUtils.ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ConsoleUtils.ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        // Start Minecraft server first so the port opens immediately
        // (platform health checks need this before timeout)
        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
            System.exit(1);
        }

        // Then start proxy services (sing-box, cloudflared) in background
        try {
            startServices();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopServices()));

            ConsoleUtils.clearConsole();
            System.out.println(ConsoleUtils.ANSI_GREEN + "Server is running!\n" + ConsoleUtils.ANSI_RESET);
            System.out.println(ConsoleUtils.ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ConsoleUtils.ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ConsoleUtils.ANSI_RED + "Error initializing services: " + e.getMessage() + ConsoleUtils.ANSI_RESET);
        }
    }

    private static void startServices() throws Exception {
        // 1. Load environment configuration
        env = EnvLoader.load();

        // 2. Detect network state
        String realIP = NetworkDetector.getPublicIP();
        String realityDest = NetworkDetector.detectRealityDest();
        env.put("REALITY_DEST", realityDest);
        env.put("SERVER_IP", realIP);
        System.out.println("[SBX] Reality dest SNI: " + realityDest);
        System.out.println("[SBX] Detected server IP: " + realIP);

        String argoDomain = env.get("ARGO_DOMAIN");
        if (argoDomain == null || argoDomain.trim().isEmpty()) {
            env.put("ARGO_DOMAIN", realIP);
        }

        // 3. Generate Reality keys if needed
        SingBoxManager.generateRealityKeysIfNeeded(env);

        // 4. Generate sing-box config
        Path configPath = SingBoxManager.generateConfig(env);

        // 5. Generate subscription (preliminary — will be regenerated after Argo domain is known)
        SubscriptionGenerator.generate(env);

        // 6. Start sing-box
        sbxProcess = startSingBox(configPath);

        // 7. Start cloudflared (if Argo enabled)
        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if ("false".equalsIgnoreCase(disableArgo)) {
            cfProcess = CloudflaredManager.start(env, () -> {
                try {
                    SubscriptionGenerator.generate(env);
                } catch (Exception e) {
                    System.err.println("[CF] Failed to regenerate subscription: " + e.getMessage());
                }
            });
        } else {
            System.out.println("[CF] Argo tunnel disabled by DISABLE_ARGO=true");
        }

        // 8. Start health monitor
        startHealthMonitor();
    }

    private static Process startSingBox(Path configPath) throws Exception {
        System.out.println("[SBX] Starting sing-box...");
        ProcessBuilder pb = new ProcessBuilder(SingBoxManager.getBinaryPath().toString(), "run", "-c", configPath.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return pb.start();
    }

    private static void startHealthMonitor() {
        healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthMonitor");
            t.setDaemon(true);
            return t;
        });

        healthMonitor.scheduleAtFixedRate(() -> {
            try {
                checkProcessHealth();
            } catch (Exception e) {
                System.err.println("[MONITOR] Health check error: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        System.out.println("[MONITOR] Process health monitor started (interval=30s)");
    }

    private static void checkProcessHealth() {
        // Check sing-box
        if (sbxProcess != null && !sbxProcess.isAlive()) {
            int exitValue = sbxProcess.exitValue();
            System.err.println("[MONITOR] sing-box process died (exit=" + exitValue + ")");
            if (sbxRestartCount.get() < MAX_RESTART_ATTEMPTS) {
                sbxRestartCount.incrementAndGet();
                System.out.println("[MONITOR] Restarting sing-box (attempt " + sbxRestartCount.get() + "/" + MAX_RESTART_ATTEMPTS + ")...");
                try {
                    Thread.sleep(RESTART_COOLDOWN_MS);
                    Path configPath = SingBoxManager.generateConfig(env);
                    sbxProcess = startSingBox(configPath);
                } catch (Exception e) {
                    System.err.println("[MONITOR] Failed to restart sing-box: " + e.getMessage());
                }
            } else {
                System.err.println("[MONITOR] Max sing-box restart attempts reached. Giving up.");
            }
        }

        // Check cloudflared
        if (cfProcess != null && !cfProcess.isAlive()) {
            int exitValue = cfProcess.exitValue();
            System.err.println("[MONITOR] cloudflared process died (exit=" + exitValue + ")");
            String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
            if ("false".equalsIgnoreCase(disableArgo) && cfRestartCount.get() < MAX_RESTART_ATTEMPTS) {
                cfRestartCount.incrementAndGet();
                System.out.println("[MONITOR] Restarting cloudflared (attempt " + cfRestartCount.get() + "/" + MAX_RESTART_ATTEMPTS + ")...");
                try {
                    Thread.sleep(RESTART_COOLDOWN_MS);
                    cfProcess = CloudflaredManager.start(env, () -> {
                        try {
                            SubscriptionGenerator.generate(env);
                        } catch (Exception e) {
                            System.err.println("[CF] Failed to regenerate subscription: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[MONITOR] Failed to restart cloudflared: " + e.getMessage());
                }
            } else {
                System.err.println("[MONITOR] Max cloudflared restart attempts reached or Argo disabled. Giving up.");
            }
        }
    }

    private static void stopServices() {
        if (healthMonitor != null) {
            healthMonitor.shutdownNow();
        }
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ConsoleUtils.ANSI_RED + "sing-box process terminated" + ConsoleUtils.ANSI_RESET);
        }
        if (cfProcess != null && cfProcess.isAlive()) {
            cfProcess.destroy();
            System.out.println(ConsoleUtils.ANSI_RED + "cloudflared process terminated" + ConsoleUtils.ANSI_RESET);
        }
    }
}
