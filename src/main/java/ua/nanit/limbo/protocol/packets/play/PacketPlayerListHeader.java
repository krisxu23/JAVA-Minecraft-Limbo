package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketPlayerListHeader implements PacketOut {
    private String header;
    private String footer;

    public void setHeader(String header) { this.header = header; }
    public void setFooter(String footer) { this.footer = footer; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeString(header);
        msg.writeString(footer);
    }
}
