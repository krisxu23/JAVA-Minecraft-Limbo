package ua.nanit.limbo.configuration;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.InetSocketAddress;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;

public class SocketAddressSerializer implements TypeSerializer<SocketAddress> {
    @Override
    public SocketAddress deserialize(Type type, ConfigurationNode node) throws Exception {
        String addr = node.getString("localhost");
        int port = node.node("port").getInt(25565);
        if (addr.equals("*") || addr.isEmpty()) {
            return new InetSocketAddress(port);
        }
        return new InetSocketAddress(addr, port);
    }

    @Override
    public void serialize(Type type, @Nullable SocketAddress obj, ConfigurationNode node) {}
}
