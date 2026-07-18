package ua.nanit.limbo.connection.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ua.nanit.limbo.server.Log;

public class ChannelTrafficHandler extends ChannelInboundHandlerAdapter {
    private final int maxPacketSize;
    private final double interval;
    private final double maxPacketRate;
    private long packetCount;
    private long lastResetTime;

    public ChannelTrafficHandler(int maxPacketSize, double interval, double maxPacketRate) {
        this.maxPacketSize = maxPacketSize;
        this.interval = interval;
        this.maxPacketRate = maxPacketRate;
        this.lastResetTime = System.nanoTime();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        long now = System.nanoTime();
        if (now - lastResetTime > interval * 1_000_000_000L) {
            packetCount = 0;
            lastResetTime = now;
        }
        packetCount++;
        if (maxPacketRate > 0 && packetCount > maxPacketRate) {
            Log.warn("Player exceeded packet rate limit");
            ctx.close();
            return;
        }
        super.channelRead(ctx, msg);
    }
}
