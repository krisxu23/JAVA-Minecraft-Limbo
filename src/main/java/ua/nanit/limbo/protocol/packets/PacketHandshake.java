package ua.nanit.limbo.protocol.packets;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.server.LimboServer;

public class PacketHandshake implements PacketIn {
    private int protocolVersion;
    private String serverAddress;
    private int serverPort;
    private State nextState;

    @Override
    public void decode(ByteMessage buf) {
        protocolVersion = buf.readVarInt();
        serverAddress = buf.readString();
        serverPort = buf.readShort();
        nextState = State.getById(buf.readVarInt());
    }

    @Override
    public void handle(ClientConnection conn, LimboServer server) {
        server.getPacketHandler().handle(conn, this);
    }

    public int getProtocolVersion() { return protocolVersion; }
    public String getServerAddress() { return serverAddress; }
    public int getServerPort() { return serverPort; }
    public State getNextState() { return nextState; }
    public Version getVersion() { return Version.getByProtocol(protocolVersion); }
}
