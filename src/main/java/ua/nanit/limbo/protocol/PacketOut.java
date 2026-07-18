package ua.nanit.limbo.protocol;

public interface PacketOut {
    void encode(ByteMessage msg, ua.nanit.limbo.protocol.registry.Version version);
}
