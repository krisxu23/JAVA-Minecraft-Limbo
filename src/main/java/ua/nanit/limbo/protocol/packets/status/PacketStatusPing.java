package ua.nanit.limbo.protocol.packets.status;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.server.LimboServer;

public class PacketStatusPing implements PacketIn {
    private long payload;

    @Override
    public void decode(ByteMessage buf) {
        payload = buf.readLong();
    }

    @Override
    public void handle(ClientConnection conn, LimboServer server) {
        conn.sendPacketAndClose(this);
    }

    public long getPayload() { return payload; }
}
