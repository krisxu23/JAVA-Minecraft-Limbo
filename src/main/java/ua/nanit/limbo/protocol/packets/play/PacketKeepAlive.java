package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketKeepAlive implements PacketOut {
    private long id;

    public void setId(long id) { this.id = id; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeVarInt(0);
        msg.writeLong(id);
    }
}
