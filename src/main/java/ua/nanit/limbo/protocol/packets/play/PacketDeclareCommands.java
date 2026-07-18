package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketDeclareCommands implements PacketOut {
    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeVarInt(0); // root node type
        msg.writeVarInt(0); // children count
        msg.writeVarInt(0); // redirect node
        msg.writeVarInt(0); // children count
        msg.writeString(""); // tooltip
        msg.writeBoolean(false); // has redirect
        msg.writeBoolean(false); // has children
    }
}
