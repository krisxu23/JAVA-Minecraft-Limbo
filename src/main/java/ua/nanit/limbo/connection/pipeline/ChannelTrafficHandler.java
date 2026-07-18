package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.nanit.limbo.server.Log;

public class ChannelTrafficHandler extends ChannelInboundHandlerAdapter {

    private final int maxPacketSize;
    private final double maxPacketRate;
    private final PacketBucket packetBucket;

    public ChannelTrafficHandler(int maxPacketSize, double interval, double maxPacketRate) {
        this.maxPacketSize = maxPacketSize;
        this.maxPacketRate = maxPacketRate;
        this.packetBucket = (interval > 0.0 && maxPacketRate > 0.0) ? new PacketBucket(interval * 1000.0, 150) : null;
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf in = (ByteBuf) msg;
            int bytes = in.readableBytes();

            if (maxPacketSize > 0 && bytes > maxPacketSize) {
                closeConnection(ctx, "Closed %s due to large packet size (%d bytes)", ctx.channel().remoteAddress(), bytes);
                return;
            }

            if (packetBucket != null) {
                packetBucket.incrementPackets(1);
                if (packetBucket.getCurrentPacketRate() > maxPacketRate) {
                    closeConnection(ctx, "Closed %s due to many packets sent (%d in the last %.1f seconds)", ctx.channel().remoteAddress(), packetBucket.sum, (packetBucket.intervalTimeNanos / 1_000_000_000.0));
                    return;
                }
            }
        }

        super.channelRead(ctx, msg);
    }

    private void closeConnection(ChannelHandlerContext ctx, String reason, Object... args) {
        ctx.close();
        Log.info(reason, args);
    }

    private static class PacketBucket {
        private final long intervalTimeNanos;
        private final long intervalResolutionNanos;
        private final int[] data;
        private int newestData;
        private long lastBucketTimeNanos;
        private int sum;

        public PacketBucket(final double intervalTime, final int totalBuckets) {
            this.intervalTimeNanos = (long) (intervalTime * 1_000_000L);
            this.intervalResolutionNanos = this.intervalTimeNanos / totalBuckets;
            this.data = new int[totalBuckets];
        }

        public void incrementPackets(final int packets) {
            long timeNs = System.nanoTime();
            long timeDelta = timeNs - this.lastBucketTimeNanos;

            if (timeDelta < 0L) {
                timeDelta = 0L;
            }

            if (timeDelta < this.intervalResolutionNanos) {
                this.data[this.newestData] += packets;
                this.sum += packets;
                return;
            }

            int bucketsToMove = (int)(timeDelta / this.intervalResolutionNanos);
            long nextBucketTime = this.lastBucketTimeNanos + bucketsToMove * this.intervalResolutionNanos;

            if (bucketsToMove >= this.data.length) {
                java.util.Arrays.fill(this.data, 0);
                this.data[0] = packets;
                this.sum = packets;
                this.newestData = 0;
                this.lastBucketTimeNanos = timeNs;
                return;
            }

            for (int i = 1; i < bucketsToMove; ++i) {
                int index = (this.newestData + i) % this.data.length;
                this.sum -= this.data[index];
                this.data[index] = 0;
            }

            int newestDataIndex = (this.newestData + bucketsToMove) % this.data.length;
            this.sum += packets - this.data[newestDataIndex];
            this.data[newestDataIndex] = packets;
            this.newestData = newestDataIndex;
            this.lastBucketTimeNanos = nextBucketTime;
        }

        public double getCurrentPacketRate() {
            return (double) this.sum / (this.intervalTimeNanos / 1_000_000_000.0);
        }
    }
}
