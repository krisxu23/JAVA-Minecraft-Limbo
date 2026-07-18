package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

public class PacketPlayerAbilities implements PacketOut {
    private int flags = 0x02;
    private float flyingSpeed = 0.05F;
    private float fieldOfView = 0.1F;

    public void setFlags(int flags) { this.flags = flags; }
    public void setFlyingSpeed(float flyingSpeed) { this.flyingSpeed = flyingSpeed; }
    public void setFieldOfView(float fieldOfView) { this.fieldOfView = fieldOfView; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeByte(flags);
        msg.writeFloat(flyingSpeed);
        msg.writeFloat(fieldOfView);
    }
}
