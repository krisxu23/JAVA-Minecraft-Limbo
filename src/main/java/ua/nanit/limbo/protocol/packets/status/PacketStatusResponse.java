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

import ua.nanit.limbo.server.PlayerSimulator;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;

public class PacketStatusResponse implements PacketOut {

    private static final String TEMPLATE = "{ \"version\": { \"name\": \"%s\", \"protocol\": %d }, \"players\": { \"max\": %d, \"online\": %d, \"sample\": %s }, \"description\": %s }";

    private LimboServer server;

    public PacketStatusResponse() { }

    public PacketStatusResponse(LimboServer server) {
        this.server = server;
    }

    @Override
    public void encode(ByteMessage msg, Version version) {
        int protocol;
        int staticProtocol =  server.getConfig().getPingData().getProtocol();

        if (staticProtocol > 0) {
            protocol = staticProtocol;
        } else {
            protocol = server.getConfig().getInfoForwarding().isNone()
                    ? version.getProtocolNumber()
                    : Version.getMax().getProtocolNumber();
        }

        String ver = server.getConfig().getPingData().getVersion();
        String desc = server.getConfig().getPingData().getDescription();

        PlayerSimulator sim = server.getPlayerSimulator();
        int simOnline = sim != null ? sim.getOnline() : 0;
        int realOnline = server.getConnections().getCount();
        int displayOnline = Math.max(realOnline, simOnline);
        String sampleJson = sim != null ? sim.getSampleJson() : "[]";

        msg.writeString(getResponseJson(ver, protocol,
                server.getConfig().getMaxPlayers(),
                displayOnline, sampleJson, desc));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private String getResponseJson(String version, int protocol, int maxPlayers, int online, String sample, String description) {
        return String.format(TEMPLATE, version, protocol, maxPlayers, online, sample, description);
    }
}

