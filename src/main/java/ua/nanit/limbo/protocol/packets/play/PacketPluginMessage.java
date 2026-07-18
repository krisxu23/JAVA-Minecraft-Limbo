package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.UuidUtil;

import java.util.UUID;

public class PacketPluginMessage implements PacketOut {
    private String channel;
    private byte[] data;

    public void setChannel(String channel) { this.channel = channel; }
    public void setData(byte[] data) { this.data = data; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(channel);
        msg.writeVarInt(data.length);
        msg.writeBytes(data);
    }
}
