package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.NbtMessageUtil;
import net.kyori.adventure.text.Component;

public class PacketTitleSetTitle implements PacketOut {
    private Component title;

    public void setTitle(Component title) { this.title = title; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(NbtMessageUtil.toJson(title));
    }
}
