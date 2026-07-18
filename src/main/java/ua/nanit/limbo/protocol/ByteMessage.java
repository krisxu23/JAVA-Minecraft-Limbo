package ua.nanit.limbo.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ByteProcessor;

import java.nio.charset.StandardCharsets;

public class ByteMessage {
    private ByteBuf buf;

    public ByteMessage() { this.buf = Unpooled.buffer(); }
    public ByteMessage(ByteBuf buf) { this.buf = buf; }

    public void writeByte(byte value) { buf.writeByte(value); }
    public void writeBytes(byte[] value) { buf.writeBytes(value); }
    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        buf.writeBytes(bytes);
    }
    public void writeVarInt(int value) {
        while ((value & 0xFFFFFF80) != 0L) {
            buf.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
    public void writeUuid(java.util.UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }
    public void writeBoolean(boolean value) { buf.writeBoolean(value); }
    public void writeInt(int value) { buf.writeInt(value); }
    public void writeLong(long value) { buf.writeLong(value); }
    public void writeShort(int value) { buf.writeShort(value); }
    public void writeFloat(float value) { buf.writeFloat(value); }
    public void writeDouble(double value) { buf.writeDouble(value); }
    public void writeEnumSet(java.util.EnumSet<?> set, Class<?> enumClass) {
        long[] bits = new long[(enumClass.getEnumConstants().length + 63) / 64];
        for (Object o : set) {
            java.lang.reflect.Enum e = (java.lang.reflect.Enum) o;
            int ordinal = e.ordinal();
            bits[ordinal / 64] |= 1L << (ordinal % 64);
        }
        for (long b : bits) writeLong(b);
    }
    public void writeNamelessCompoundTag(CompoundBinaryTag tag) {
        buf.writeBytes(tag.getBytes());
    }

    public byte readByte() { return buf.readByte(); }
    public void readBytes(byte[] dst) { buf.readBytes(dst); }
    public int readVarInt() {
        int result = 0;
        int bits = 0;
        byte b;
        while (true) {
            b = buf.readByte();
            result |= (b & 0x7F) << bits;
            bits++;
            if (bits > 28) throw new RuntimeException("VarInt too big");
            if ((b & 0x80) == 0) break;
        }
        return result;
    }
    public String readString() {
        int len = readVarInt();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public java.util.UUID readUuid() {
        return new java.util.UUID(buf.readLong(), buf.readLong());
    }
    public boolean readBoolean() { return buf.readBoolean(); }
    public int readInt() { return buf.readInt(); }
    public long readLong() { return buf.readLong(); }
    public int readShort() { return buf.readShort(); }

    public int readableBytes() { return buf.readableBytes(); }
    public void getBytes(int index, byte[] dest) { buf.getBytes(index, dest); }
    public ByteBuf unwrap() { return buf; }

    public byte[] toByteArray() {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
