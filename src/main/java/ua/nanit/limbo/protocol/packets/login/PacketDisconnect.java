package ua.nanit.limbo.protocol.packets.login;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketDisconnect implements PacketOut {
    private String reason;

    public void setReason(String reason) { this.reason = reason; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(reason);
    }
}
