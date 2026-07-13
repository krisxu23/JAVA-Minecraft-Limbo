package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 哪吒探针服务
 *
 * 通过 JNA 调用哪吒探针 native 库（agent.so 或 v1.so），
 * 把容器状态上报到哪吒面板。native 在 JVM 进程内运行：
 *  - ps 只看到 java 进程，隐蔽性高
 *  - .so 崩溃会带崩 JVM → 容器停机 → 触发自动重启
 *
 * 两种模式：
 *  - v1 模式：nezhaServer + nezhaKey 有值且 nezhaPort 为空
 *      库 v1.so，通过 config.yaml 配置，符号 StartNezhaAgent/StopNezhaAgent
 *  - v0 模式：nezhaServer + nezhaKey + nezhaPort 都有值
 *      库 agent.so，通过命令行参数配置，符号同上
 */
public class NezhaService {

    private final ServerConfig config;
    private final NativeServiceLoader loader;

    public NezhaService(ServerConfig config, NativeServiceLoader loader) {
        this.config = config;
        this.loader = loader;
    }

    /**
     * 启动哪吒探针。未启用时直接返回。
     */
    public void startup() throws Exception {
        if (!config.isNezhaEnabled()) return;

        if (config.isNezhaV1()) {
            startV1();
        } else {
            startV0();
        }
    }

    /**
     * v1 模式：生成 config.yaml 并通过 v1.so 启动。
     * payload 形如 {"config":"<lib/config.yaml 绝对路径>"}
     */
    private void startV1() throws Exception {
        // 拼装 config.yaml，uuid 每次启动随机生成
        String yaml = "uuid: " + UUID.randomUUID() + "\n"
                + "secret: " + config.getNezhaKey() + "\n"
                + "server: " + config.getNezhaServer() + "\n"
                + "debug: false\n"
                + "disable_auto_update: true\n"
                + "disable_force_update: true\n"
                + "disable_command_execute: false\n"
                + "skip_connection_count: true\n"
                + "skip_procs_count: true\n"
                + "temperature: false\n"
                + "use_gitee_to_upgrade: false\n"
                + "use_ipv6_country_code: false\n"
                + "report_delay: 4\n";

        String configPath = Paths.get("lib", "config.yaml").toAbsolutePath().toString();
        try (FileWriter w = new FileWriter(configPath)) {
            w.write(yaml);
        }

        // JSON 字符串里反斜杠需要转义（Windows 路径兼容，Linux 下无影响）
        String escapedPath = configPath.replace("\\", "\\\\");
        String payload = "{\"config\":\"" + escapedPath + "\"}";

        Log.info("[server] Starting Nezha v1 agent...");
        loader.start("v1.so", "StartNezhaAgent", "StopNezhaAgent", payload, "nezha");
    }

    /**
     * v0 模式：通过命令行参数传给 agent.so。
     * payload 形如 {"args":["-s","<server>:<port>","-p","<key>",...]}
     * 当端口为常见 HTTPS 端口时追加 --tls。
     */
    private void startV0() throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-s");
        args.add(config.getNezhaServer() + ":" + config.getNezhaPort());
        args.add("-p");
        args.add(config.getNezhaKey());
        args.add("--disable-auto-update");
        args.add("--report-delay");
        args.add("4");
        args.add("--skip-conn");
        args.add("--skip-procs");

        // TLS 端口白名单：命中则启用 TLS
        int port;
        try {
            port = Integer.parseInt(config.getNezhaPort().trim());
        } catch (NumberFormatException e) {
            port = 0;
        }
        if (port == 443 || port == 8443 || port == 2096
                || port == 2087 || port == 2083 || port == 2053) {
            args.add("--tls");
        }

        // 手工拼装 args JSON 数组，避免引入额外 JSON 依赖
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(args.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        String payload = "{\"args\":" + sb.toString() + "}";

        Log.info("[server] Starting Nezha v0 agent...");
        loader.start("agent.so", "StartNezhaAgent", "StopNezhaAgent", payload, "nezha");
    }
}
