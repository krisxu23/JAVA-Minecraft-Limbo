package ua.nanit.limbo.protocol.packets.login;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.server.LimboServer;

public class PacketLoginAcknowledged implements PacketIn {
    @Override
    public void decode(ByteMessage buf) {}

    @Override
    public void handle(ClientConnection conn, LimboServer server) {
        server.getPacketHandler().handle(conn, this);
    }
}
