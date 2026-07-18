package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.HashMap;
import java.util.Map;

public class PacketEncoder {
    private State state = State.STATUS;
    private Version clientVersion = Version.V1_7;
    private final Map<Class<? extends PacketOut>, Integer> classToId = new HashMap<>();
    private final Map<Integer, Class<? extends PacketOut>> idToClass = new HashMap<>();

    public void updateState(State state) { this.state = state; }
    public void updateVersion(Version version) { this.clientVersion = version; }

    public void encode(ChannelHandlerContext ctx, PacketOut packet) throws Exception {
        Class<? extends PacketOut> cls = packet.getClass();
        Integer id = classToId.get(cls);
        if (id == null) return;

        ByteBuf buf = ctx.alloc().buffer();
        try {
            buf.writeVarInt(id);
            packet.encode(new ua.nanit.limbo.protocol.ByteMessage(buf), clientVersion);
            ctx.write(buf);
        } catch (Exception e) {
            buf.release();
            throw e;
        }
    }

    public void register(Class<? extends PacketOut> cls, int id) {
        classToId.put(cls, id);
        idToClass.put(id, cls);
    }
}
