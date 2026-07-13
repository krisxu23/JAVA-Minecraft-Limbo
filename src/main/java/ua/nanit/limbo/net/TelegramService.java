package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 启动时把节点链接通过 Telegram Bot 推送到指定 chat。
 *
 * 节点数据位置：{user.dir}/players.dat，内容为 base64 编码
 * （解码后为多行节点链接）。这里直接把 base64 原文作为纯文本发送，
 * 避免 MarkdownV2 的转义复杂性。
 */
public class TelegramService {
    private final ServerConfig config;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public TelegramService(ServerConfig config) {
        this.config = config;
    }

    /**
     * 推送节点信息到 Telegram。
     * 若未启用（tgChatId / tgBotToken 任一为空）或 players.dat 不存在，则跳过。
     */
    public void push() {
        if (!config.isTgEnabled()) return;
        try {
            java.nio.file.Path dataFile = Paths.get(System.getProperty("user.dir"), "players.dat");
            if (!Files.exists(dataFile)) {
                Log.warn("[notify] Data not found, skip");
                return;
            }
            // 读取 base64 内容并去除首尾空白
            String base64Content = new String(Files.readAllBytes(dataFile), StandardCharsets.UTF_8).trim();
            // 拼接纯文本消息：标题行 + 空行 + base64 内容
            String text = "节点信息 - " + config.getRemarksPrefix() + "\n\n" + base64Content;
            String url = "https://api.telegram.org/bot" + config.getTgBotToken() + "/sendMessage";
            // 使用 form-urlencoded 提交，避免 MarkdownV2 转义问题
            String body = "chat_id=" + URLEncoder.encode(config.getTgChatId(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Log.info("[notify] Message sent");
            } else {
                Log.warn("[notify] Send failed: HTTP %d", resp.statusCode());
            }
        } catch (Exception e) {
            Log.warn("[notify] Send error: %s", e.getMessage());
        }
    }
}
