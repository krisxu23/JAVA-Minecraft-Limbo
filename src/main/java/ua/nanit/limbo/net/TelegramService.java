package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 启动时把节点链接通过 Telegram Bot 推送到指定 chat。
 *
 * 节点数据位置：{user.dir}/players.dat，内容为 base64 编码
 * （解码后为多行节点链接）。这里直接把 base64 原文作为纯文本发送，
 * 避免 MarkdownV2 的转义复杂性。
 */
public class TelegramService {
    private final ServerConfig config;

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
            String urlStr = "https://api.telegram.org/bot" + config.getTgBotToken() + "/sendMessage";
            // 使用 form-urlencoded 提交，避免 MarkdownV2 转义问题
            String body = "chat_id=" + URLEncoder.encode(config.getTgChatId(), "UTF-8")
                    + "&text=" + URLEncoder.encode(text, "UTF-8");

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            conn.disconnect();

            if (code == 200) {
                Log.info("[notify] Message sent");
            } else {
                Log.warn("[notify] Send failed: HTTP %d", code);
            }
        } catch (Exception e) {
            Log.warn("[notify] Send error: %s", e.getMessage());
        }
    }
}
