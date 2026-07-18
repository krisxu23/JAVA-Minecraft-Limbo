package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.data.BossBar;
import ua.nanit.limbo.util.NbtMessageUtil;

import java.util.UUID;

public class PacketBossBar implements PacketOut {
    private UUID uuid;
    private BossBar bossBar;

    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeUuid(uuid);
        msg.writeVarInt(0); // action: add
        msg.writeString(NbtMessageUtil.toJson(bossBar.getText()));
        msg.writeFloat(bossBar.getHealth());
        msg.writeByte(bossBar.getColor().ordinal());
        msg.writeByte(bossBar.getDivision().ordinal());
        msg.writeByte(0x01); // flags: darken sky
    }
}
