package ua.nanit.limbo.proxy;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ua.nanit.limbo.server.Log;

/**
 * Manages cloudflared (Argo Tunnel): downloads the binary, starts fixed-tunnel
 * or quick-tunnel mode, discovers the trycloudflare.com domain,
 * and runs the process with a self-healing watchdog.
 *
 * The watchdog restarts cloudflared on non-zero exit (3s delay) and
 * stops on clean exit (0) or JVM shutdown.
 */
public final class CloudflaredManager {

    private static volatile Process currentProcess;

    private static final Pattern QUICK_TUNNEL_PATTERN = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private CloudflaredManager() {}

    // ==================== Self-Healing Watchdog ====================

    /**
     * Starts cloudflared with a self-healing loop. Blocks until the process
     * exits cleanly (exit=0) or the current thread is interrupted.
     * Intended to run on a dedicated daemon thread.
     *
     * Subscription is regenerated after the Argo domain is discovered
     * (quick tunnel) or immediately (fixed tunnel with known domain).
     */
    public static void runWithSelfHealing(Map<String, String> env) {
        Runnable onDomainDiscovered = () -> {
            try {
                SubscriptionGenerator.generate(env);
            } catch (Exception e) {
                Log.warn("[CF] Failed to regenerate subscription: " + e.getMessage());
            }
        };

        while (true) {
            try {
                currentProcess = startOnce(env, onDomainDiscovered);
                int exitCode = currentProcess.waitFor();
                if (exitCode == 0) {
                    Log.info("[CF] cloudflared exited cleanly (exit=0), stopping watchdog");
                    break;
                }
                Log.warn("[CF] cloudflared died (exit=" + exitCode + "), restarting in 3s...");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.warn("[CF] Watchdog interrupted, stopping");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.warn("[CF] Error: " + e.getMessage() + ", restarting in 10s...");
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Terminates the current cloudflared process (if alive).
     * Called from the JVM shutdown hook.
     */
    public static void shutdown() {
        Process p = currentProcess;
        if (p != null && p.isAlive()) {
            Log.info("[CF] Terminating cloudflared...");
            p.destroy();
        }
    }

    // ==================== One-shot Start ====================

    /**
     * Starts cloudflared Argo tunnel once. For fixed tunnels (with ARGO_AUTH), the tunnel
     * starts immediately. For quick tunnels (no token), a daemon thread monitors
     * output to discover the *.trycloudflare.com domain.
     *
     * @param env                   environment configuration map (ARGO_DOMAIN may be written for quick tunnel)
     * @param onDomainDiscovered    callback invoked after the Argo domain is known (for subscription regeneration)
     * @return the cloudflared Process handle
     */
    private static Process startOnce(Map<String, String> env, Runnable onDomainDiscovered) throws Exception {
        Path cfPath = getBinaryPath(env);
        String argoAuth = env.getOrDefault("ARGO_AUTH", "");
        String argoPort = env.getOrDefault("ARGO_PORT", "8001");
        Process cfProcess;

        if (!argoAuth.isEmpty()) {
            // Fixed tunnel with token
            Log.info("[CF] Starting Argo fixed tunnel...");
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
                Log.info("[CF] Argo fixed tunnel started, domain: " + argoDomain);
            }
        } else {
            // Quick tunnel (no token) â€?parse output for trycloudflare.com domain
            Log.info("[CF] Starting Argo quick tunnel...");
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
                            Log.info("[CF] Argo quick tunnel domain: " + host);
                            onDomainDiscovered.run();
                            Log.info("[CF] Subscription updated with Argo domain");
                        }
                    }
                } catch (Exception e) {
                    Log.warn("[CF] Parser thread error: " + e.getMessage());
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
            Log.info("[CF] Downloading cloudflared " + version + " (" + arch + ")...");
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, binPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!binPath.toFile().setExecutable(true)) {
                throw new IOException("Failed to set cloudflared executable permission");
            }
            Log.info("[CF] cloudflared downloaded successfully");
        }

        return binPath;
    }
}
