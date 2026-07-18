package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketTitleTimes implements PacketOut {
    private int fadeIn, stay, fadeOut;

    public void setFadeIn(int fadeIn) { this.fadeIn = fadeIn; }
    public void setStay(int stay) { this.stay = stay; }
    public void setFadeOut(int fadeOut) { this.fadeOut = fadeOut; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeVarInt(fadeIn);
        msg.writeVarInt(stay);
        msg.writeVarInt(fadeOut);
    }
}
