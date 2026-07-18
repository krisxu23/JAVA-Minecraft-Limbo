package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.HashMap;
import java.util.Map;

public class PacketDecoder {
    private State state = State.STATUS;
    private Version clientVersion = Version.V1_7;
    private final Map<Integer, Map<State, Class<? extends PacketIn>>> packetMap = new HashMap<>();

    public void updateState(State state) { this.state = state; }
    public void updateVersion(Version version) { this.clientVersion = version; }

    public PacketIn decode(ByteBuf buf) {
        ByteMessage msg = new ByteMessage(buf);
        int packetId = msg.readVarInt();

        Class<? extends PacketIn> cls = getPacketClass(packetId, state);
        if (cls != null) {
            try {
                PacketIn pkt = cls.getDeclaredConstructor().newInstance();
                pkt.decode(msg);
                return pkt;
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private Class<? extends PacketIn> getPacketClass(int id, State state) {
        Map<State, Class<? extends PacketIn>> stateMap = packetMap.get(id);
        if (stateMap != null) return stateMap.get(state);
        return null;
    }

    public void register(int id, State state, Class<? extends PacketIn> cls) {
        packetMap.computeIfAbsent(id, k -> new HashMap<>()).put(state, cls);
    }
}
