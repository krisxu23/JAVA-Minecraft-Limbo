package ua.nanit.limbo.connection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.ReadTimeoutHandler;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;
import ua.nanit.limbo.server.PlayerSimulator;
import ua.nanit.limbo.connection.pipeline.ChannelTrafficHandler;
import ua.nanit.limbo.connection.pipeline.VarIntFrameDecoder;
import ua.nanit.limbo.connection.pipeline.VarIntLengthEncoder;

import java.util.HashMap;
import java.util.Map;

public class ClientChannelInitializer extends io.netty.channel.ChannelInitializer<io.netty.channel.Channel> {
    private final LimboServer server;

    public ClientChannelInitializer(LimboServer server) {
        this.server = server;
    }

    @Override
    protected void initChannel(io.netty.channel.Channel ch) {
        io.netty.channel.ChannelPipeline pipeline = ch.pipeline();

        long readTimeout = server.getConfig().getReadTimeout();
        if (readTimeout > 0) {
            pipeline.addLast("timeout", new ReadTimeoutHandler(readTimeout / 1000));
        }

        pipeline.addLast("frameDecoder", new VarIntFrameDecoder());
        pipeline.addLast("lengthEncoder", new VarIntLengthEncoder());

        if (server.getConfig().isUseTrafficLimits()) {
            pipeline.addLast("traffic", new ChannelTrafficHandler(
                server.getConfig().getMaxPacketSize(),
                server.getConfig().getInterval(),
                server.getConfig().getMaxPacketRate()
            ));
        }

        PacketDecoder decoder = new PacketDecoder();
        PacketEncoder encoder = new PacketEncoder();
        pipeline.addLast("decoder", decoder);
        pipeline.addLast("encoder", encoder);
        pipeline.addLast("handler", new ClientConnection(ch, server, decoder, encoder));
    }
}
