package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketPlayerPositionAndLook implements PacketOut {
    private double x, y, z, yaw, pitch;
    private int teleportId;
    private int deltaBits;

    public PacketPlayerPositionAndLook(double x, double y, double z, float yaw, float pitch, int teleportId) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.teleportId = teleportId;
    }

    public void setDeltaBits(int deltaBits) { this.deltaBits = deltaBits; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeDouble(x);
        msg.writeDouble(y);
        msg.writeDouble(z);
        msg.writeFloat((float) yaw);
        msg.writeFloat((float) pitch);

        // 1.9+ includes teleport ID
        if (version.moreOrEqual(Version.V1_9)) {
            msg.writeVarInt(teleportId);
        }

        // 1.19.3+ includes delta bits
        if (version.moreOrEqual(Version.V1_19_3)) {
            msg.writeVarInt(deltaBits);
        }
    }
}
