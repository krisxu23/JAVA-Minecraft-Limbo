package ua.nanit.limbo.protocol;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;

import java.nio.charset.StandardCharsets;

public class NbtMessage {
    private CompoundBinaryTag tag;

    public NbtMessage() {}
    public NbtMessage(CompoundBinaryTag tag) { this.tag = tag; }

    public CompoundBinaryTag getTag() { return tag; }
    public void setTag(CompoundBinaryTag tag) { this.tag = tag; }

    public static NbtMessage fromJson(String json) {
        CompoundBinaryTag tag = TagStringIO.stringLoader().load(json);
        return new NbtMessage(tag);
    }

    public static NbtMessage fromBinary(byte[] bytes) {
        CompoundBinaryTag tag = BinaryTagIo.DEFAULT_CODEC().parse(bytes);
        return new NbtMessage((CompoundBinaryTag) tag);
    }

    public byte[] toBytes() {
        return BinaryTagIo.DEFAULT_CODEC().serialize(tag);
    }

    public static class CompoundBinaryTag {
        private byte[] bytes;
        public CompoundBinaryTag(byte[] bytes) { this.bytes = bytes; }
        public byte[] getBytes() { return bytes; }
    }
}
