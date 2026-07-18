package ua.nanit.limbo.connection.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

public class VarIntByteDecoder extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        int readerIndex = buf.readerIndex();
        if (buf.getBytes(readerIndex) == 0x00) {
            out.add(buf.readByte());
        } else {
            buf.readerIndex(readerIndex);
            int length = readVarIntSize(buf);
            if (buf.isReadable(length)) {
                out.add(buf.readBytes(length));
            }
        }
    }

    private int readVarIntSize(ByteBuf buf) {
        int i = 0;
        int j = 0;
        while (true) {
            int k = buf.readByte();
            i |= (k & 0x7F) << j++ * 7;
            if (j > 5) throw new RuntimeException("VarInt too big");
            if ((k & 0x80) == 0) break;
        }
        return i;
    }
}
