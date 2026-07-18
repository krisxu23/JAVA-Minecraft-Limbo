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

package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

/**
 * Declares available commands to the client (1.13+).
 * Sends only a root node — no commands registered.
 */
public class PacketDeclareCommands implements PacketOut {

    @Override
    public void encode(ByteMessage msg, Version version) {
        // 1 node: the root node with 0 children
        msg.writeVarInt(1);
        msg.writeByte(0);       // root node flags
        msg.writeVarInt(0);     // 0 children
        msg.writeVarInt(0);     // 0 root node indices
    }
}
