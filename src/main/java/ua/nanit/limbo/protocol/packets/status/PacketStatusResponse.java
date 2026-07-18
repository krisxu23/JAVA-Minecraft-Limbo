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

package ua.nanit.limbo.protocol.packets.status;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class PacketStatusResponse implements PacketOut {

    private static final String TEMPLATE = "{ \"version\": { \"name\": \"%s\", \"protocol\": %d }, \"players\": { \"max\": %d, \"online\": %d, \"sample\": [] }, \"description\": %s, \"favicon\": \"%s\" }";
    private static final String TEMPLATE_NO_ICON = "{ \"version\": { \"name\": \"%s\", \"protocol\": %d }, \"players\": { \"max\": %d, \"online\": %d, \"sample\": [] }, \"description\": %s }";

    private LimboServer server;
    private String cachedIcon;

    public PacketStatusResponse() { }

    public PacketStatusResponse(LimboServer server) {
        this.server = server;
    }

    @Override
    public void encode(ByteMessage msg, Version version) {
        int protocol;
        String ver;
        String desc;
        int maxPlayers;
        int online;

        // Use disguise config when enabled (from minewire)
        if (server.getConfig().isDisguiseEnable()) {
            ver = server.getConfig().getDisguiseVersionName();
            protocol = server.getConfig().getDisguiseProtocolId();
            desc = "{\"text\":\"" + server.getConfig().getDisguiseMotd().replace("\"", "\\\"") + "\"}";
            maxPlayers = server.getConfig().getMaxPlayers();
            online = server.getPlayerCountSimulator() != null
                    ? server.getPlayerCountSimulator().getOnline()
                    : server.getConnections().getCount();
        } else {
            int staticProtocol = server.getConfig().getPingData().getProtocol();
            if (staticProtocol > 0) {
                protocol = staticProtocol;
            } else {
                protocol = server.getConfig().getInfoForwarding().isNone()
                        ? version.getProtocolNumber()
                        : Version.getMax().getProtocolNumber();
            }
            ver = server.getConfig().getPingData().getVersion();
            desc = server.getConfig().getPingData().getDescription();
            maxPlayers = server.getConfig().getMaxPlayers();
            online = server.getConnections().getCount();
        }

        // Load server icon (64x64 PNG → base64)
        String icon = getIconBase64();
        if (icon != null) {
            msg.writeString(getResponseJson(ver, protocol, maxPlayers, online, desc, icon));
        } else {
            msg.writeString(getResponseJsonNoIcon(ver, protocol, maxPlayers, online, desc));
        }
    }

    private String getIconBase64() {
        if (cachedIcon != null) return cachedIcon;
        if (!server.getConfig().isDisguiseEnable()) return null;

        String iconPath = server.getConfig().getDisguiseIconPath();
        if (iconPath == null || iconPath.isEmpty()) return null;

        try {
            byte[] iconBytes = Files.readAllBytes(Paths.get(iconPath));
            cachedIcon = "data:image/png;base64," + Base64.getEncoder().encodeToString(iconBytes);
            return cachedIcon;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private String getResponseJson(String version, int protocol, int maxPlayers, int online, String description, String icon) {
        return String.format(TEMPLATE, version, protocol, maxPlayers, online, description, icon);
    }

    private String getResponseJsonNoIcon(String version, int protocol, int maxPlayers, int online, String description) {
        return String.format(TEMPLATE_NO_ICON, version, protocol, maxPlayers, online, description);
    }
}
