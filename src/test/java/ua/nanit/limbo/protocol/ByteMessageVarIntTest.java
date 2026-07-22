package ua.nanit.limbo.protocol;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ByteMessageVarIntTest {

    @Test
    void writeAndReadVarInt() {
        ByteMessage msg = new ByteMessage(Unpooled.buffer());
        msg.writeVarInt(0);
        assertEquals(0, msg.readVarInt());
    }

    @Test
    void writeAndReadVarIntLarge() {
        ByteMessage msg = new ByteMessage(Unpooled.buffer());
        msg.writeVarInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, msg.readVarInt());
    }

    @Test
    void writeAndReadVarIntNegative() {
        ByteMessage msg = new ByteMessage(Unpooled.buffer());
        msg.writeVarInt(-1);
        assertEquals(-1, msg.readVarInt());
    }

    @Test
    void writeAndReadVarInt300() {
        ByteMessage msg = new ByteMessage(Unpooled.buffer());
        msg.writeVarInt(300);
        assertEquals(300, msg.readVarInt());
    }
}