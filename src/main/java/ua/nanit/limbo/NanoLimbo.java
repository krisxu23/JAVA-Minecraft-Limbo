package ua.nanit.limbo;

import ua.nanit.limbo.proxy.*;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.util.Map;

/**
 * Entry point for the NanoLimbo proxy + Minecraft limbo server.
 * Starts the Minecraft limbo first (for platform health checks),
 * then starts proxy services (sing-box / cloudflared via native libs).
 */
public final class NanoLimbo {

    public static void main(String[] args) {

        double classVersion = Double.parseDouble(System.getProperty("java.class.version"));
        if (classVersion < 52.0) {
            System.err.println(ConsoleUtils.ANSI_RED + "ERROR: Your Java version is too low (class version " + classVersion + "). Java 8 (52.0) or higher is required!" + ConsoleUtils.ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(1);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
            System.exit(1);
        }

        try {
            startServices();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                SingBoxManager.shutdown();
                CloudflaredManager.shutdown();
                System.out.println(ConsoleUtils.ANSI_RED + "Proxy services terminated" + ConsoleUtils.ANSI_RESET);
            }));

            Thread.sleep(5000);
            ConsoleUtils.clearConsole();
            System.out.println(ConsoleUtils.ANSI_GREEN + "Server is running!\n" + ConsoleUtils.ANSI_RESET);
            System.out.println(ConsoleUtils.ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ConsoleUtils.ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ConsoleUtils.ANSI_RED + "Error initializing services: " + e.getMessage() + ConsoleUtils.ANSI_RESET);
            e.printStackTrace();
        }
    }

    private static void startServices() throws Exception {
        Map<String, String> env = EnvLoader.load();

        String realIP = NetworkDetector.getPublicIP();
        String realityDest = NetworkDetector.detectRealityDest();
        env.put("REALITY_DEST", realityDest);
        env.put("SERVER_IP", realIP);
        System.out.println("[SBX] Reality dest SNI: " + realityDest);
        System.out.println("[SBX] Detected server IP: " + realIP);

        String argoDomain = env.get("ARGO_DOMAIN");
        if (argoDomain == null || argoDomain.trim().isEmpty()) {
            env.put("ARGO_DOMAIN", "");
        }

        SingBoxManager.generateRealityKeysIfNeeded(env);
        SubscriptionGenerator.generate(env);
        SingBoxManager.start(env);

        String disableArgo = env.getOrDefault("DISABLE_ARGO", "false");
        if ("false".equalsIgnoreCase(disableArgo)) {
            CloudflaredManager.start(env);
        } else {
            System.out.println("[CF] Argo tunnel disabled by DISABLE_ARGO=true");
        }
    }
}
