package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.NbtMessageUtil;
import net.kyori.adventure.text.Component;

public class PacketTitleSetSubTitle implements PacketOut {
    private Component subtitle;

    public void setSubtitle(Component subtitle) { this.subtitle = subtitle; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(NbtMessageUtil.toJson(subtitle));
    }
}
