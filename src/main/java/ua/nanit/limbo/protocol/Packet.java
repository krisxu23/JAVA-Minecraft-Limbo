package ua.nanit.limbo.protocol;

public interface Packet {
    void handle(ua.nanit.limbo.connection.ClientConnection conn, ua.nanit.limbo.server.LimboServer server);
}
