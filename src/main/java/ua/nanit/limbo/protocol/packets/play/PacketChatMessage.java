package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketChatMessage implements PacketOut {
    private String message;
    private PositionLegacy position;

    public enum PositionLegacy { CHAT, SYSTEM_MESSAGE, GAME_INFO }

    public void setMessage(String message) { this.message = message; }
    public void setPosition(PositionLegacy position) { this.position = position; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(message);
        if (version.less(Version.V1_19)) {
            msg.writeByte(position.ordinal());
        }
    }
}
