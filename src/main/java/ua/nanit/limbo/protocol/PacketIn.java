package ua.nanit.limbo.protocol;

public interface PacketIn {
    void decode(ByteMessage buf);
    void handle(ua.nanit.limbo.connection.ClientConnection conn, ua.nanit.limbo.server.LimboServer server);
}
