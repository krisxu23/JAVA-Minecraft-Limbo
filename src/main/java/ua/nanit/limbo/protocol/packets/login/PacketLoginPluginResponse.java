package ua.nanit.limbo.protocol.packets.login;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.server.LimboServer;

public class PacketLoginPluginResponse implements PacketIn {
    private int messageId;
    private boolean successful;
    private ByteMessage data;

    @Override
    public void decode(ByteMessage buf) {
        messageId = buf.readVarInt();
        successful = buf.readBoolean();
        if (successful) {
            int len = buf.readVarInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            data = new ByteMessage();
            data.writeBytes(bytes);
        }
    }

    @Override
    public void handle(ClientConnection conn, LimboServer server) {
        server.getPacketHandler().handle(conn, this);
    }

    public int getMessageId() { return messageId; }
    public boolean isSuccessful() { return successful; }
    public ByteMessage getData() { return data; }
}
