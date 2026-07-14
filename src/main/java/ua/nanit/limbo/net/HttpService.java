package ua.nanit.limbo.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import ua.nanit.limbo.server.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * 轻量 HTTP 服务（订阅 + 伪装站二合一）
 * 用 JDK 内置 HttpServer，零依赖，内存占用 < 1MB
 * - 访问 /<subPath>  返回 players.dat 的 base64 内容（订阅链接）
 * - 访问 / 或其他路径 返回博客伪装页面
 */
public class HttpService {

    private final ServerConfig config;
    private HttpServer server;

    public HttpService(ServerConfig config) {
        this.config = config;
    }

    public void startup() throws Exception {
        boolean hasSub = config.isSubEnabled();
        boolean hasWeb = config.isWebEnabled();
        if (!hasSub && !hasWeb) return;

        int port;
        if (hasSub) {
            port = Integer.parseInt(config.getSubPort().trim());
        } else {
            port = Integer.parseInt(config.getWebPort().trim());
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        if (hasSub) {
            server.createContext("/" + config.getSubPath(), new SubHandler());
        }
        server.createContext("/", new WebHandler());
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
        Log.info("[server] Web service started on port %d", port);
    }

    /**
     * 订阅处理器：读取 players.dat（已是 base64 编码的节点链接）并原样返回。
     * 文件不存在则返回 404。
     */
    private class SubHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path dataFile = Paths.get(System.getProperty("user.dir"), "players.dat");
            if (!Files.exists(dataFile)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] content = Files.readAllBytes(dataFile);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Server", "nginx");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private class WebHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            byte[] bytes;
            int status;

            if (path.equals("/favicon.ico")) {
                exchange.getResponseHeaders().set("Content-Type", "image/x-icon");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if (path.equals("/") || path.equals("/index.html") || path.equals("/index.htm")) {
                bytes = buildPage().getBytes(StandardCharsets.UTF_8);
                status = 200;
            } else {
                bytes = build404().getBytes(StandardCharsets.UTF_8);
                status = 404;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Server", "nginx");
            exchange.getResponseHeaders().set("Connection", "close");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String buildPage() {
            String title = esc(config.getWebTitle());
            String desc = esc(config.getWebDesc());
            String year = String.valueOf(java.time.Year.now().getValue());
            return "<!DOCTYPE html>\n"
                    + "<html lang=\"en\">\n<head>\n"
                    + "<meta charset=\"UTF-8\">\n"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                    + "<meta name=\"generator\" content=\"Hugo 0.121.0\">\n"
                    + "<meta name=\"description\" content=\"" + desc + "\">\n"
                    + "<title>" + title + "</title>\n"
                    + "<style>\n"
                    + "  *{box-sizing:border-box;margin:0;padding:0}\n"
                    + "  body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;"
                    + "color:#333;background:#fafafa;line-height:1.6;max-width:720px;margin:0 auto;padding:40px 20px}\n"
                    + "  header{border-bottom:2px solid #e0e0e0;padding-bottom:16px;margin-bottom:32px}\n"
                    + "  header h1{font-size:28px;font-weight:700;color:#222}\n"
                    + "  header p{color:#666;margin-top:4px;font-size:14px}\n"
                    + "  main h2{font-size:22px;margin:24px 0 12px;color:#222}\n"
                    + "  main p{margin-bottom:14px;color:#444}\n"
                    + "  main article{margin-bottom:32px;padding-bottom:24px;border-bottom:1px solid #eee}\n"
                    + "  main article h3{font-size:18px;margin-bottom:8px}\n"
                    + "  main article h3 a{color:#2563eb;text-decoration:none}\n"
                    + "  main article h3 a:hover{text-decoration:underline}\n"
                    + "  main article .meta{font-size:13px;color:#999;margin-bottom:8px}\n"
                    + "  code{background:#f0f0f0;padding:2px 6px;border-radius:3px;font-family:'SFMono-Regular',Consolas,monospace;font-size:13px}\n"
                    + "  footer{margin-top:40px;padding-top:20px;border-top:2px solid #e0e0e0;color:#999;font-size:13px;text-align:center}\n"
                    + "  footer a{color:#666;text-decoration:none}\n"
                    + "  @media (max-width:600px){body{padding:24px 16px}header h1{font-size:24px}}\n"
                    + "</style>\n</head>\n<body>\n"
                    + "<header>\n"
                    + "  <h1>" + title + "</h1>\n"
                    + "  <p>" + desc + "</p>\n"
                    + "</header>\n"
                    + "<main>\n"
                    + "  <h2>Recent Posts</h2>\n"
                    + "  <article>\n"
                    + "    <h3><a href=\"/post/building-a-static-site/\">Building a Static Site with Hugo</a></h3>\n"
                    + "    <div class=\"meta\">January 4, " + year + " &middot; 6 min read</div>\n"
                    + "    <p>A walkthrough of how I migrated my blog from WordPress to Hugo and deployed it on a small VPS. Includes notes on theme customization and CI setup.</p>\n"
                    + "  </article>\n"
                    + "  <article>\n"
                    + "    <h3><a href=\"/post/understanding-tcp-keepalive/\">Understanding TCP Keepalive</a></h3>\n"
                    + "    <div class=\"meta\">December 18, " + (Integer.parseInt(year) - 1) + " &middot; 8 min read</div>\n"
                    + "    <p>How <code>SO_KEEPALIVE</code> actually works on Linux, the default kernel parameters, and how to tune them for long-lived connections behind NAT.</p>\n"
                    + "  </article>\n"
                    + "  <article>\n"
                    + "    <h3><a href=\"/post/notes-on-go-122/\">Notes on Go 1.22</a></h3>\n"
                    + "    <div class=\"meta\">December 2, " + (Integer.parseInt(year) - 1) + " &middot; 4 min read</div>\n"
                    + "    <p>Quick notes on the new <code>for</code> loop semantics, range over integers, and the enhanced <code>net/http</code> routing improvements.</p>\n"
                    + "  </article>\n"
                    + "  <article>\n"
                    + "    <h3><a href=\"/post/migrating-to-caddy/\">Migrating from Nginx to Caddy</a></h3>\n"
                    + "    <div class=\"meta\">November 15, " + (Integer.parseInt(year) - 1) + " &middot; 5 min read</div>\n"
                    + "    <p>Automatic HTTPS, simpler config, and where Caddy still falls short of Nginx for production workloads.</p>\n"
                    + "  </article>\n"
                    + "</main>\n"
                    + "<footer>\n"
                    + "  &copy; " + year + " " + title + " &middot; <a href=\"/about/\">About</a> &middot; <a href=\"/feed.xml\">RSS</a>\n"
                    + "</footer>\n"
                    + "</body>\n</html>\n";
        }

        private String build404() {
            return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
                    + "<meta charset=\"UTF-8\">\n<title>404 - Not Found</title>\n"
                    + "<style>body{font-family:-apple-system,sans-serif;color:#666;text-align:center;padding:80px 20px}\n"
                    + "h1{font-size:64px;color:#ddd;margin-bottom:8px}\np{font-size:16px}</style>\n"
                    + "</head>\n<body>\n<h1>404</h1>\n<p>Sorry, the page you are looking for does not exist.</p>\n"
                    + "<p><a href=\"/\" style=\"color:#2563eb;text-decoration:none\">Back to Home</a></p>\n"
                    + "</body>\n</html>\n";
        }

        private String esc(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
        }
    }
}
