package ua.nanit.limbo.util;

import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagIo;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.NbtMessage;

public class NbtMessageUtil {
    private static final BinaryTagIo TAG_IO = BinaryTagIo.DEFAULT_CODEC();

    public static net.kyori.adventure.text.Component parse(String json) {
        try {
            return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(json);
        } catch (Exception e) {
            return net.kyori.adventure.text.Component.text(json);
        }
    }

    public static String toJson(net.kyori.adventure.text.Component component) {
        try {
            return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static byte[] serializeCompound(CompoundBinaryTag tag) {
        try {
            return TAG_IO.serialize(tag);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static CompoundBinaryTag deserializeCompound(byte[] bytes) {
        try {
            return (CompoundBinaryTag) TAG_IO.parse(bytes);
        } catch (Exception e) {
            return CompoundBinaryTag.empty();
        }
    }
}
