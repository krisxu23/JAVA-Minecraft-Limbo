package ua.nanit.limbo.proxy;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages cloudflared (Argo Tunnel): downloads the binary, starts fixed-tunnel
 * or quick-tunnel mode, and discovers the trycloudflare.com domain.
 * Extracted from NanoLimbo.java during refactoring.
 */
public final class CloudflaredManager {

    private static final Pattern QUICK_TUNNEL_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private CloudflaredManager() {}

    /**
     * Starts cloudflared Argo tunnel. For fixed tunnels (with ARGO_AUTH), the tunnel
     * starts immediately. For quick tunnels (no token), a daemon thread monitors
     * output to discover the *.trycloudflare.com domain.
     *
     * @param env                   environment configuration map (ARGO_DOMAIN may be written for quick tunnel)
     * @param onDomainDiscovered    callback invoked after the Argo domain is known (for subscription regeneration)
     * @return the cloudflared Process handle (for lifecycle management)
     */
    public static Process start(Map<String, String> env, Runnable onDomainDiscovered) throws Exception {
        Path cfPath = getBinaryPath(env);
        String argoAuth = env.getOrDefault("ARGO_AUTH", "");
        String argoPort = env.getOrDefault("ARGO_PORT", "8001");
        Process cfProcess;

        if (!argoAuth.isEmpty()) {
            // Fixed tunnel with token
            System.out.println("[CF] Starting Argo fixed tunnel...");
            ProcessBuilder pb = new ProcessBuilder(cfPath.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "run", "--token", argoAuth);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            cfProcess = pb.start();

            // Invoke callback immediately for fixed tunnels (domain is already known)
            String argoDomain = env.getOrDefault("ARGO_DOMAIN", "");
            if (!argoDomain.isEmpty()) {
                onDomainDiscovered.run();
                System.out.println("[CF] Argo fixed tunnel started, domain: " + argoDomain);
            }
        } else {
            // Quick tunnel (no token) — parse output for trycloudflare.com domain
            System.out.println("[CF] Starting Argo quick tunnel...");
            ProcessBuilder pb = new ProcessBuilder(cfPath.toString(),
                "tunnel", "--no-autoupdate", "--edge-ip-version", "auto",
                "--protocol", "http2", "--url", "http://localhost:" + argoPort);
            pb.redirectErrorStream(true);
            cfProcess = pb.start();

            AtomicBoolean domainFound = new AtomicBoolean(false);
            Thread parserThread = new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(cfProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null && !domainFound.get()) {
                        Matcher matcher = QUICK_TUNNEL_PATTERN.matcher(line);
                        if (matcher.find()) {
                            String domain = matcher.group();
                            String host = new URL(domain).getHost();
                            env.put("ARGO_DOMAIN", host);
                            domainFound.set(true);
                            System.out.println("[CF] Argo quick tunnel domain: " + host);
                            onDomainDiscovered.run();
                            System.out.println("[CF] Subscription updated with Argo domain");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[CF] Parser thread error: " + e.getMessage());
                }
            });
            parserThread.setDaemon(true);
            parserThread.start();
        }

        return cfProcess;
    }

    // ==================== Binary Management ====================

    /**
     * Returns the path to the cloudflared binary, downloading it if necessary.
     */
    public static Path getBinaryPath(Map<String, String> env) throws IOException {
        String version = env.getOrDefault("CF_VERSION", "2025.10.0");
        String osArch = System.getProperty("os.arch").toLowerCase();
        String osName = System.getProperty("os.name").toLowerCase();

        String arch;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "amd64";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "arm64";
        } else {
            throw new RuntimeException("Unsupported architecture for cloudflared: " + osArch);
        }

        String platform = osName.contains("linux") ? "linux" :
                          osName.contains("mac") ? "darwin" : "linux";

        String filename = "cloudflared-" + platform + "-" + arch;
        String url = "https://github.com/cloudflare/cloudflared/releases/download/" + version + "/" + filename;

        Path binPath = Paths.get(System.getProperty("java.io.tmpdir"), "cf");

        if (!binPath.toFile().exists()) {
            System.out.println("[CF] Downloading cloudflared " + version + " (" + arch + ")...");
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, binPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!binPath.toFile().setExecutable(true)) {
                throw new IOException("Failed to set cloudflared executable permission");
            }
            System.out.println("[CF] cloudflared downloaded successfully");
        }

        return binPath;
    }
}
