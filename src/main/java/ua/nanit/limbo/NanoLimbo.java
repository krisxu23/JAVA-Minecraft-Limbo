/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo;

import ua.nanit.limbo.net.ServerConfig;
import ua.nanit.limbo.net.ServiceManager;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static ServiceManager serviceManager;

    public static void main(String[] args) {

        if (Float.parseFloat(System.getProperty("java.class.version")) < 52.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too low, please use Java 8 or higher!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }

        // ================================================================
        //                    用户配置区（可直接修改）
        //         在 GitHub 网页上修改下面的值，保存后会自动构建
        //      如果运行时也设置了同名环境变量，环境变量会覆盖这里的值
        // ================================================================
        ServerConfig config = ServerConfig.getInstance();
        // UUID（留空自动生成随机UUID，也可在此填写固定值）
        config.setUuid("5c002620-79a3-4417-bc96-86490f2c2fbd");                            // 节点UUID，留空自动生成
        config.setDomain("");                          // 服务器域名或IP
        config.setPort("25565");                                  // Minecraft伪装端口
        config.setRemarksPrefix("Rustix");                           // 节点备注前缀

        // sing-box 版本
        config.setSbVersion("1.13.14");                           // sing-box版本号

        // Argo 隧道配置
        config.setWsPort("8001");                                 // VMess+WS端口（Argo转发用）
        config.setArgoDomain("ruxtis.5566248.cc.cd");                                 // Argo固定隧道域名，留空用临时隧道
        config.setArgoToken("eyJhIjoiN2ZiY2U5ZDc0OGM0MjU5OGZiZjkyYTM5ZjY5MDZkYmIiLCJ0IjoiMDA2MzI4OGYtOGU5Ni00MzhlLWI3ZWQtNzRiN2U4MmRlNDNhIiwicyI6Ik9HSTBaVGsxT0RJdE56VmpPUzAwTVdReUxXSm1PREF0WkRFM1pXUmpORE01TldKaiJ9");                                  // Argo固定隧道token，留空用临时隧道
        config.setArgoVersion("2025.10.0");                       // cloudflared版本号

        // 各协议端口配置（留空=不启用，填端口=启用）
        config.setRealityPort("33959");                                // VLESS+Reality端口(TCP)
        config.setHy2Port("33959");                                    // Hysteria2端口(UDP)
        config.setTuicPort("38919");                                   // TUIC端口(UDP)
        config.setSocks5Port("38919");                                 // SOCKS5端口(TCP)
        config.setAnytlsPort("");                                 // AnyTLS端口(TCP)

        // 各协议密码（留空自动生成）
        config.setTuicPassword("");                               // TUIC密码，留空=用UUID
        config.setSocks5User("");                                 // SOCKS5用户名，留空=xah
        config.setSocks5Password("");                             // SOCKS5密码，留空=用UUID
        config.setAnytlsPassword("");                             // AnyTLS密码，留空=用UUID

        // 优选IP/域名
        config.setCfIp("www.wto.org");                        // 优选域名或IP
        config.setCfPort("443");                                  // 优选端口

        // HTTP 伪装站配置（留空=不启用，填端口=启用）
        // 免费单端口容器默认不启用，避免占用唯一端口；多端口容器可通过环境变量 WEB_PORT 启用
        config.setWebPort("");                                      // HTTP伪装端口，如8080，留空不启用
        config.setWebTitle("Personal Blog");                   // 网站标题
        config.setWebDesc("Thoughts, code and notes");         // 网站描述

        // HTTP 订阅服务（访问 http://域名:subPort/sub 拿节点）
        config.setSubPort("");                                 // 订阅端口，留空=不启用，如 "3000"
        config.setSubPath("sub");                              // 订阅路径 token

        // 哪吒探针配置（留空=不启用）
        config.setNezhaServer("");                             // 哪吒服务器地址，如 "na.baidu.com"
        config.setNezhaPort("");                               // 哪吒端口：留空=v1模式，填端口=v0模式，如 "443"
        config.setNezhaKey("");                                // 哪吒 agent 密钥

        // Telegram 推送（留空=不启用）
        config.setTgChatId("");                                // TG chat_id，如 "123456789"
        config.setTgBotToken("");                              // TG bot token

        // 自动保活（防止容器休眠，留空=不启用）
        config.setAutoAccess(false);                           // true=注册到 oooo.serv00.net 保活
        config.setProjectUrl("");                              // 本项目公网URL，如 "https://xxx.com"

        // 节点上传到 Merge-sub（留空=不启用）
        config.setUploadUrl("");                               // Merge-sub API 地址

        // WARP 出站（YouTube 走 WARP）
        config.setYtWarpOut(false);                           // true=YouTube 流量走 WARP

        // Argo 隧道开关
        config.setDisableArgo(false);                         // true=完全禁用 Argo（只用 Reality/Hy2 等直连）
        // ================================================================
        //                    用户配置区结束
        // ================================================================

        // 环境变量覆盖（运行时设置的环境变量优先于上面的值）
        config.loadFromEnv();

        // 清理临时文件（bridge.log 等）
        try {
            java.nio.file.Path bridgeLog = java.nio.file.Paths.get("lib/bridge.log");
            java.nio.file.Path singboxLog = java.nio.file.Paths.get("lib/singbox.log");
            if (java.nio.file.Files.exists(bridgeLog)) {
                java.nio.file.Files.delete(bridgeLog);
            }
            if (java.nio.file.Files.exists(singboxLog)) {
                java.nio.file.Files.delete(singboxLog);
            }
        } catch (Exception e) {
            // 忽略清理失败
        }

        // 启动后台服务
        try {
            serviceManager = new ServiceManager();
            serviceManager.install();
            serviceManager.startup();

            // 优雅关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(ANSI_RED + "Server stopping..." + ANSI_RESET);
                if (serviceManager != null) {
                    serviceManager.shutdown();
                }
            }));
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error during startup: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }

        // 启动 Minecraft Limbo 服务器（伪装层）
        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }
}
