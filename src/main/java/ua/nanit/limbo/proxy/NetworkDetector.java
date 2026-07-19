package ua.nanit.limbo.proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

/**
 * Network detection utilities: public IP discovery, Reality dest SNI probing,
 * and DNS strategy detection. Extracted from NanoLimbo.java during refactoring.
 */
public final class NetworkDetector {

    private NetworkDetector() {}

    /**
     * Detects the public IPv4 address by querying well-known IP echo services
     * with automatic fallback.
     */
    public static String getPublicIP() {
        try {
            String[] services = {
                "https://api.ipify.org",
                "https://ifconfig.me",
                "https://icanhazip.com",
                "https://ipinfo.io/ip"
            };
            for (String service : services) {
                try {
                    URL url = new URL(service);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String ip = reader.readLine().trim();
                    reader.close();
                    if (ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        return ip;
                    }
                } catch (Exception e) {
                    // Try next service
                }
            }
        } catch (Exception e) {
            System.err.println("[SBX] Failed to detect public IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    /**
     * Auto-detects a reachable Reality dest SNI by testing TCP connectivity
     * to a list of known hosts on port 443. Returns the first reachable host.
     */
    public static String detectRealityDest() {
        String[] hosts = {
            "www.iij.ad.jp", "www.microsoft.com", "www.bing.com",
            "www.apple.com", "www.joom.com", "www.amazon.com"
        };
        for (String host : hosts) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, 443), 2000);
                socket.close();
                return host;
            } catch (Exception e) {
                // try next host
            }
        }
        return "www.iij.ad.jp"; // all failed, use fallback default
    }

    /**
     * Detects DNS connectivity strategy by testing IPv4 and IPv6 DNS servers.
     * Returns "prefer_ipv4" or "prefer_ipv6".
     */
    public static String detectDNSStrategy() {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress("8.8.8.8", 53), 2000);
            s.close();
            return "prefer_ipv4";
        } catch (Exception e) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress("2001:4860:4860::8888", 53), 2000);
                s.close();
                return "prefer_ipv6";
            } catch (Exception e2) {
                return "prefer_ipv4";
            }
        }
    }
}
