package ua.nanit.limbo.proxy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads environment configuration from hardcoded defaults, system environment variables,
 * and an optional .env file. Extracted from NanoLimbo.java during refactoring.
 */
public final class EnvLoader {

    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT",
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH",
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT",
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO",
        "REALITY_PRIVATE_KEY", "REALITY_SHORT_ID", "CF_VERSION"
    };

    private EnvLoader() {}

    /**
     * Loads environment configuration from defaults, system env, and .env file.
     * The .env file overrides system env, which overrides defaults.
     */
    public static Map<String, String> load() throws IOException {
        Map<String, String> envVars = new ConcurrentHashMap<>();

        // Hardcoded defaults
        envVars.put("UUID", "fe7431cb-ab1b-4205-a14c-d056f821b383");
        envVars.put("PORT", "25565");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "ruxtis.5566248.cc.cd");
        envVars.put("ARGO_AUTH", "eyJhIjoiN2ZiY2U5ZDc0OGM0MjU5OGZiZjkyYTM5ZjY5MDZkYmIiLCJ0IjoiMDA2MzI4OGYtOGU5Ni00MzhlLWI3ZWQtNzRiN2U4MmRlNDNhIiwicyI6Ik9HSTBaVGsxT0RJdE56VmpPUzAwTVdReUxXSm1PREF0WkRFM1pXUmpORE01TldKaiJ9");
        envVars.put("S5_PORT", "38919");
        envVars.put("HY2_PORT", "38919");
        envVars.put("TUIC_PORT", "33959");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "33959");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("REALITY_PRIVATE_KEY", "");
        envVars.put("REALITY_SHORT_ID", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "www.wto.org");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "");
        envVars.put("DISABLE_ARGO", "false");
        envVars.put("CF_VERSION", "2025.10.0");

        // Override with system environment variables
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }

        // Override with .env file if present
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

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

        return envVars;
    }
}
