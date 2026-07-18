package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketSpawnPosition implements PacketOut {
    private int x, y, z;

    public PacketSpawnPosition(int x, int y, int z) {
        this.x = x; this.y = y; this.z = z;
    }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeBlockPos(x, y, z);
        msg.writeVarInt(0);
    }
}
