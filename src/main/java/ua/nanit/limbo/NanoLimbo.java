package ua.nanit.limbo;

import ua.nanit.limbo.proxy.*;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for the NanoLimbo proxy + Minecraft limbo server.
 * Orchestrates proxy service startup (sing-box, cloudflared) and then
 * starts the Minecraft server. Refactored from a ~758-line god class
 * into a thin orchestrator delegating to focused service classes.
 */
public final class NanoLimbo {

    private static Process sbxProcess;
    private static Process cfProcess;

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

        try {
            startServices();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopServices()));

            Thread.sleep(15000);
            ConsoleUtils.clearConsole();
            System.out.println(ConsoleUtils.ANSI_GREEN + "Server is running!\n" + ConsoleUtils.ANSI_RESET);
            System.out.println(ConsoleUtils.ANSI_GREEN + "Thank you for using this script,Enjoy!\n" + ConsoleUtils.ANSI_RESET);
            Thread.sleep(5000);
        } catch (Exception e) {
            System.err.println(ConsoleUtils.ANSI_RED + "Error initializing services: " + e.getMessage() + ConsoleUtils.ANSI_RESET);
        }

        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
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

        // 3. Generate Reality keys if needed
        SingBoxManager.generateRealityKeysIfNeeded(env);

        // 4. Generate sing-box config
        Path configPath = SingBoxManager.generateConfig(env);

        // 5. Generate subscription (preliminary — will be regenerated after Argo domain is known)
        SubscriptionGenerator.generate(env);

        // 6. Start sing-box
        System.out.println("[SBX] Starting sing-box...");
        ProcessBuilder pb = new ProcessBuilder(SingBoxManager.getBinaryPath().toString(), "run", "-c", configPath.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        sbxProcess = pb.start();

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
    }

    private static void stopServices() {
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
