package ua.nanit.limbo;

import ua.nanit.limbo.proxy.*;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.util.Map;

/**
 * Entry point for the NanoLimbo proxy + Minecraft limbo server.
 * Orchestrates proxy service startup (sing-box, cloudflared) and then
 * starts the Minecraft server.
 *
 * Each proxy service runs its own self-healing watchdog thread:
 * on non-zero exit the process restarts after 3s; on clean exit (0)
 * or JVM shutdown the watchdog stops.
 */
public final class NanoLimbo {

    public static void main(String[] args) {

        double classVersion = Double.parseDouble(System.getProperty("java.class.version"));
        if (classVersion < 52.0) {
            System.err.println(ConsoleUtils.ANSI_RED + "ERROR: Your Java version is too low (class version " + classVersion + "). Java 8 (52.0) or higher is required!" + ConsoleUtils.ANSI_RESET);
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

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                SingBoxManager.shutdown();
                CloudflaredManager.shutdown();
                System.out.println(ConsoleUtils.ANSI_RED + "Proxy services terminated" + ConsoleUtils.ANSI_RESET);
            }));

            ConsoleUtils.clearConsole();
            System.out.println(ConsoleUtils.ANSI_GREEN + "Server is running!\n" + ConsoleUtils.ANSI_RESET);
            System.out.println(ConsoleUtils.ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ConsoleUtils.ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ConsoleUtils.ANSI_RED + "Error initializing services: " + e.getMessage() + ConsoleUtils.ANSI_RESET);
        }
    }

    private static void startServices() throws Exception {
        // 1. Load environment configuration
        Map<String, String> env = EnvLoader.load();

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

        // 3. Generate Reality keys if needed (pure Java, no binary required)
        SingBoxManager.generateRealityKeysIfNeeded(env);

        // 4. Generate subscription (preliminary — will be regenerated after Argo domain is known)
        SubscriptionGenerator.generate(env);

        // 5. Start sing-box via JNA native .so (runs in background daemon thread)
        SingBoxManager.start(env);

        // 6. Start cloudflared via JNA native .so (if Argo enabled)
        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if ("false".equalsIgnoreCase(disableArgo)) {
            CloudflaredManager.start(env);
        } else {
            System.out.println("[CF] Argo tunnel disabled by DISABLE_ARGO=true");
        }
    }
}
