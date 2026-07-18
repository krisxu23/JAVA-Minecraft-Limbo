package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketEmptyChunk implements PacketOut {
    private int x, z;
    private int[] biomes;

    public void setX(int x) { this.x = x; }
    public void setZ(int z) { this.z = z; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeVarInt(0); // ground up continuous
        msg.writeLongArray(new long[0]); // blob index
        msg.writeBoolean(true); // primary

        if (version.moreOrEqual(Version.V1_17)) {
            msg.writeVarInt(0); // section count
        } else {
            msg.writeShort(0);
        }

        // Biome data
        if (biomes == null) {
            biomes = new int[256];
            for (int i = 0; i < 256; i++) biomes[i] = 0;
        }
        msg.writeIntArray(biomes);
    }
}
