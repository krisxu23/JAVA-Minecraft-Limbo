package ua.nanit.limbo.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.ArrayList;
import java.util.List;

public class PacketSnapshot {
    private final byte[] bytes;
    private final int length;

    private PacketSnapshot(byte[] bytes) {
        this.bytes = bytes;
        this.length = bytes.length;
    }

    public static PacketSnapshot of(PacketOut packet, Version version) {
        ByteBuf buf = Unpooled.buffer(256);
        packet.encode(new ByteMessage(buf), version);
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);
        return new PacketSnapshot(data);
    }

    public static PacketSnapshot of(PacketOut packet) {
        ByteBuf buf = Unpooled.buffer(256);
        packet.encode(new ByteMessage(buf), Version.V1_21);
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(0, data);
        return new PacketSnapshot(data);
    }

    public void writeTo(ByteBuf buf) {
        buf.writeBytes(bytes);
    }

    public int getLength() { return length; }
    public byte[] getBytes() { return bytes; }
}
