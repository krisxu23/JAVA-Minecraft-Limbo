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

import ua.nanit.limbo.proxy.ProxyConfig;
import ua.nanit.limbo.proxy.ProxyManager;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public final class NanoLimbo {

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";
    private static ProxyManager proxyManager;

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
        ProxyConfig config = ProxyConfig.getInstance();
        config.setUuid("2523c510-9ff0-415b-9582-93949bfae7e3");  // 节点UUID，不同平台部署需更改
        config.setDomain("example.com");                          // 服务器域名或IP
        config.setPort("25565");                                  // Minecraft伪装端口
        config.setRemarksPrefix("xah");                           // 节点备注前缀

        // sing-box 版本
        config.setSingboxVersion("1.13.14");                      // sing-box版本号

        // Argo 隧道配置
        config.setWsPort("8001");                                 // VMess+WS端口（Argo转发用）
        config.setArgoDomain("");                                 // Argo固定隧道域名，留空用临时隧道
        config.setArgoToken("");                                  // Argo固定隧道token，留空用临时隧道
        config.setArgoVersion("2025.10.0");                       // cloudflared版本号

        // 各协议端口配置（留空=不启用，填端口=启用）
        config.setRealityPort("");                                // VLESS+Reality端口(TCP)
        config.setHy2Port("");                                    // Hysteria2端口(UDP)
        config.setTuicPort("");                                   // TUIC端口(UDP)
        config.setSsPort("");                                     // Shadowsocks端口(TCP)
        config.setTrojanPort("");                                 // Trojan端口(TCP)

        // 各协议密码（留空自动生成）
        config.setTuicPassword("");                               // TUIC密码，留空=用UUID
        config.setSsPassword("");                                 // SS密码，留空=随机生成
        config.setTrojanPassword("");                             // Trojan密码，留空=用UUID

        // 优选IP/域名
        config.setCfIp("www.shopify.com");                        // 优选域名或IP
        config.setCfPort("443");                                  // 优选端口
        // ================================================================
        //                    用户配置区结束
        // ================================================================

        // 环境变量覆盖（运行时设置的环境变量优先于上面的值）
        config.loadFromEnv();

        // Start proxy services (sing-box + Argo)
        try {
            proxyManager = new ProxyManager();
            proxyManager.install();
            proxyManager.startup();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(ANSI_RED + "Shutting down..." + ANSI_RESET);
            }));

            System.out.println(ANSI_GREEN + "\nProxy services are starting..." + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using JAVA-singbox!\n" + ANSI_RESET);
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing proxy services: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }

        // Start Minecraft Limbo server (camouflage)
        try {
            new LimboServer().start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }
}
