/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo.protocol;

import io.netty.buffer.*;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.kyori.adventure.nbt.*;
import ua.nanit.limbo.protocol.registry.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight wrapper around Netty's ByteBuf providing Minecraft protocol read/write methods.
 * <p>
 * Does NOT extend ByteBuf — only exposes commonly used ByteBuf operations directly,
 * plus Minecraft protocol helpers (VarInt, String, UUID, NBT, etc.).
 */
public class ByteMessage {

    private final ByteBuf buf;

    public ByteMessage(ByteBuf buf) {
        this.buf = buf;
    }

    /** Returns the underlying Netty ByteBuf for direct operations. */
    public ByteBuf buf() {
        return buf;
    }

    /** Creates a new ByteMessage backed by an unpooled heap buffer. */
    public static ByteMessage create() {
        return new ByteMessage(Unpooled.buffer());
    }

    /* ========================================================================
     *  Minecraft Protocol Methods
     * ======================================================================== */

    public int readVarInt() {
        int i = 0;
        int maxRead = Math.min(5, buf.readableBytes());

        for (int j = 0; j < maxRead; j++) {
            int k = buf.readByte();
            i |= (k & 0x7F) << j * 7;
            if ((k & 0x80) != 128) {
                return i;
            }
        }

        buf.skipBytes(maxRead);

        throw new IllegalArgumentException("Cannot read VarInt");
    }

    public void writeVarInt(int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else {
            writeVarIntFull(value);
        }
    }

    private void writeVarIntFull(final int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            buf.writeMedium(w);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | (value >>> 21);
            buf.writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            buf.writeInt(w);
            buf.writeByte(value >>> 28);
        }
    }

    public String readString() {
        return readString(readVarInt());
    }

    public String readString(int length) {
        String str = buf.toString(buf.readerIndex(), length, StandardCharsets.UTF_8);
        buf.skipBytes(length);
        return str;
    }

    public void writeString(CharSequence str) {
        int size = ByteBufUtil.utf8Bytes(str);
        writeVarInt(size);
        buf.writeCharSequence(str, StandardCharsets.UTF_8);
    }

    public byte[] readBytesArray() {
        int length = readVarInt();
        byte[] array = new byte[length];
        buf.readBytes(array);
        return array;
    }

    public void writeBytesArray(byte[] array) {
        writeVarInt(array.length);
        buf.writeBytes(array);
    }

    public int[] readIntArray() {
        int len = readVarInt();
        int[] array = new int[len];
        for (int i = 0; i < len; i++) {
            array[i] = readVarInt();
        }
        return array;
    }

    public UUID readUuid() {
        long msb = buf.readLong();
        long lsb = buf.readLong();
        return new UUID(msb, lsb);
    }

    public void writeUuid(UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public String[] readStringsArray() {
        int length = readVarInt();
        String[] ret = new String[length];
        for (int i = 0; i < length; i++) {
            ret[i] = readString();
        }
        return ret;
    }

    public void writeStringsArray(String[] stringArray) {
        writeVarInt(stringArray.length);
        for (String str : stringArray) {
            writeString(str);
        }
    }

    public void writeVarIntArray(int[] array) {
        writeVarInt(array.length);
        for (int i : array) {
            writeVarInt(i);
        }
    }

    public void writeLongArray(long[] array) {
        writeVarInt(array.length);
        for (long i : array) {
            buf.writeLong(i);
        }
    }

    public void writeCompoundTagArray(CompoundBinaryTag[] compoundTags) {
        try (ByteBufOutputStream stream = new ByteBufOutputStream(buf)) {
            writeVarInt(compoundTags.length);

            for (CompoundBinaryTag tag : compoundTags) {
                BinaryTagIO.writer().write(tag, (OutputStream) stream);
            }
        }
        catch (IOException e) {
            throw new EncoderException("Cannot write NBT CompoundTag");
        }
    }

    public CompoundBinaryTag readCompoundTag() {
        try (ByteBufInputStream stream = new ByteBufInputStream(buf)) {
            return BinaryTagIO.reader().read((InputStream) stream);
        }
        catch (IOException thrown) {
            throw new DecoderException("Cannot read NBT CompoundTag");
        }
    }

    public void writeCompoundTag(CompoundBinaryTag compoundTag) {
        try (ByteBufOutputStream stream = new ByteBufOutputStream(buf)) {
            BinaryTagIO.writer().write(compoundTag, (OutputStream) stream);
        }
        catch (IOException e) {
            throw new EncoderException("Cannot write NBT CompoundTag");
        }
    }

    public void writeNamelessCompoundTag(BinaryTag binaryTag) {
        try (ByteBufOutputStream stream = new ByteBufOutputStream(buf)) {
            stream.writeByte(binaryTag.type().id());
            NbtWriter writer = NBT_WRITERS.get(binaryTag.getClass());
            if (writer != null) {
                writer.write(binaryTag, stream);
            }
        }
        catch (IOException e) {
            throw new EncoderException("Cannot write NBT tag");
        }
    }

    @FunctionalInterface
    private interface NbtWriter {
        void write(BinaryTag tag, ByteBufOutputStream stream) throws IOException;
    }

    private static final Map<Class<?>, NbtWriter> NBT_WRITERS = Map.ofEntries(
            Map.entry(CompoundBinaryTag.class, (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(ByteBinaryTag.class,    (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(ShortBinaryTag.class,   (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(IntBinaryTag.class,     (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(LongBinaryTag.class,    (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(FloatBinaryTag.class,   (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(DoubleBinaryTag.class,  (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(StringBinaryTag.class,  (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(ListBinaryTag.class,    (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s)),
            Map.entry(EndBinaryTag.class,     (BinaryTag t, ByteBufOutputStream s) -> t.type().write(t, s))
    );

    public void writeNbtMessage(NbtMessage nbtMessage, Version version) {
        if (version.moreOrEqual(Version.V1_20_3)) {
            writeNamelessCompoundTag(nbtMessage.getTag());
        }
        else {
            writeString(nbtMessage.getJson());
        }
    }

    public <E extends Enum<E>> void writeEnumSet(EnumSet<E> enumset, Class<E> oclass) {
        E[] enums = oclass.getEnumConstants();
        BitSet bits = new BitSet(enums.length);

        for (int i = 0; i < enums.length; ++i) {
            bits.set(i, enumset.contains(enums[i]));
        }

        buf.writeBytes(Arrays.copyOf(bits.toByteArray(), (enums.length + 8) >> 3));
    }

    /** Copies readable bytes into a new byte array. */
    public byte[] toByteArray() {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /* ========================================================================
     *  Commonly Used ByteBuf Operations (delegated to wrapped buffer)
     * ======================================================================== */

    public int readableBytes()                          { return buf.readableBytes(); }
    public int readerIndex()                            { return buf.readerIndex(); }
    public int writerIndex()                            { return buf.writerIndex(); }

    public boolean readBoolean()                        { return buf.readBoolean(); }
    public byte readByte()                              { return buf.readByte(); }
    public short readUnsignedByte()                     { return buf.readUnsignedByte(); }
    public short readShort()                            { return buf.readShort(); }
    public int readUnsignedShort()                      { return buf.readUnsignedShort(); }
    public int readMedium()                             { return buf.readMedium(); }
    public int readInt()                                { return buf.readInt(); }
    public long readLong()                              { return buf.readLong(); }
    public float readFloat()                            { return buf.readFloat(); }
    public double readDouble()                          { return buf.readDouble(); }
    public ByteBuf readBytes(int length)                { return buf.readBytes(length); }
    public ByteBuf readBytes(byte[] dst)                { return buf.readBytes(dst); }
    public ByteBuf readBytes(ByteBuf dst)               { return buf.readBytes(dst); }
    public ByteBuf readBytes(ByteBuf dst, int length)   { return buf.readBytes(dst, length); }
    public ByteBuf readSlice(int length)                { return buf.readSlice(length); }
    public ByteBuf readRetainedSlice(int length)        { return buf.readRetainedSlice(length); }
    public ByteBuf skipBytes(int length)                { return buf.skipBytes(length); }
    public ByteBuf getBytes(int index, byte[] dst)      { return buf.getBytes(index, dst); }
    public ByteBuf getBytes(int index, ByteBuf dst)     { return buf.getBytes(index, dst); }

    public ByteBuf writeBoolean(boolean value)          { return buf.writeBoolean(value); }
    public ByteBuf writeByte(int value)                 { return buf.writeByte(value); }
    public ByteBuf writeShort(int value)                { return buf.writeShort(value); }
    public ByteBuf writeMedium(int value)               { return buf.writeMedium(value); }
    public ByteBuf writeInt(int value)                  { return buf.writeInt(value); }
    public ByteBuf writeLong(long value)                { return buf.writeLong(value); }
    public ByteBuf writeFloat(float value)              { return buf.writeFloat(value); }
    public ByteBuf writeDouble(double value)            { return buf.writeDouble(value); }
    public ByteBuf writeBytes(byte[] src)               { return buf.writeBytes(src); }
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) { return buf.writeBytes(src, srcIndex, length); }
    public ByteBuf writeBytes(ByteBuf src)              { return buf.writeBytes(src); }
    public ByteBuf writeBytes(ByteBuf src, int length)  { return buf.writeBytes(src, length); }
    public ByteBuf writeZero(int length)                { return buf.writeZero(length); }
    public ByteBuf ensureWritable(int len)              { return buf.ensureWritable(len); }
    public boolean release()                            { return buf.release(); }
    public int refCnt()                                 { return buf.refCnt(); }
}