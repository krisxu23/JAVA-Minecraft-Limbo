package ua.nanit.limbo.protocol.packets.login;

import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.UuidUtil;

import java.util.UUID;

public class PacketLoginSuccess implements PacketOut {
    private String username;
    private UUID uuid;

    public void setUsername(String username) { this.username = username; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(username);
        msg.writeUuid(uuid);
    }
}
