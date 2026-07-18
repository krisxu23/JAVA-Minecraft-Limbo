package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import java.util.List;

public class VarIntFrameDecoder extends MessageToMessageCodec<ByteBuf, ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        out.add(msg);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        msg.markReaderIndex();
        int value = 0;
        int i = 0;
        while (true) {
            if (!msg.isReadable()) {
                msg.resetReaderIndex();
                return;
            }
            byte b = msg.readByte();
            value |= (b & 0x7F) << i * 7;
            i++;
            if (i > 5) { throw new RuntimeException("VarInt too big"); }
            if ((b & 0x80) == 0) break;
        }
        if (value > 1048576) { throw new RuntimeException("Packet too big: " + value); }
        if (msg.readableBytes() < value) {
            msg.resetReaderIndex();
            return;
        }
        out.add(msg.readBytes(value));
    }
}
