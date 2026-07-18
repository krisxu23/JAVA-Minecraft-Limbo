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

import ua.nanit.limbo.net.PlayerSimulator;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;

public class PacketStatusResponse implements PacketOut {

    private LimboServer server;

    public PacketStatusResponse() { }

    public PacketStatusResponse(LimboServer server) {
        this.server = server;
    }

    @Override
    public void encode(ByteMessage msg, Version version) {
        int protocol;
        int staticProtocol = server.getConfig().getPingData().getProtocol();

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

        // desc and sampleJson are server-generated/configured, trusted as valid JSON
        String json = "{\"version\":{\"name\":\"" + ver + "\",\"protocol\":" + protocol
                + "},\"players\":{\"max\":" + server.getConfig().getMaxPlayers()
                + ",\"online\":" + displayOnline
                + ",\"sample\":" + sampleJson
                + "},\"description\":" + desc + "}";

        msg.writeString(json);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
