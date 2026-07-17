package ua.nanit.limbo.protocol;

import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-encoded packet cache. Encodes to byte[] on first use per version,
 * rather than pre-computing all versions at startup.
 */
public class PacketSnapshot implements PacketOut {

    private final PacketOut packet;
    private final ConcurrentHashMap<Version, byte[]> cache = new ConcurrentHashMap<>();
    private volatile int cachedPacketId = -1;

    public PacketSnapshot(PacketOut packet) {
        this.packet = packet;
    }

    public PacketOut getWrappedPacket() {
        return packet;
    }

    public int getPacketId(State.PacketRegistry registry) {
        if (cachedPacketId == -1) {
            cachedPacketId = registry.getPacketId(packet.getClass());
        }
        return cachedPacketId;
    }

    @Override
    public void encode(ByteMessage msg, Version version) {
        byte[] data = cache.computeIfAbsent(version, this::encodeForVersion);
        msg.writeBytes(data);
    }

    private byte[] encodeForVersion(Version version) {
        ByteMessage buf = ByteMessage.create();
        try {
            packet.encode(buf, version);
            return buf.toByteArray();
        } finally {
            buf.release();
        }
    }

    @Override
    public String toString() {
        return packet.getClass().getSimpleName();
    }

    public static PacketSnapshot of(PacketOut packet) {
        return new PacketSnapshot(packet);
    }

    public int cachedVersionCount() {
        return cache.size();
    }
}
