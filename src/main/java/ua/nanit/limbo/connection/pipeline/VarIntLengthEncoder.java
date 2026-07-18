package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class VarIntLengthEncoder extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int length = msg.readableBytes();
        writeVarInt(out, length);
        out.writeBytes(msg);
    }

    private void writeVarInt(ByteBuf buf, int value) {
        while ((value & 0xFFFFFF80) != 0L) {
            buf.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}
