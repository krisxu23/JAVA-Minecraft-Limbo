package ua.nanit.limbo.util;

import com.google.gson.*;
import net.kyori.adventure.nbt.*;
import ua.nanit.limbo.protocol.NbtMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NbtMessageUtil {

    public static NbtMessage create(String json) {
        BinaryTag compoundBinaryTag = fromJson(JsonParser.parseString(json));

        return new NbtMessage(json, compoundBinaryTag);
    }

    public static BinaryTag fromJson(JsonElement json) {
        if (json instanceof JsonPrimitive) {
            JsonPrimitive jsonPrimitive = (JsonPrimitive) json;
            if (jsonPrimitive.isNumber()) {
                // Gson's LazilyParsedNumber does NOT extend Byte/Short/Integer/Long/Float,
                // so instanceof checks always fail. Use BigDecimal to detect integer vs float.
                java.math.BigDecimal bd = jsonPrimitive.getAsBigDecimal();
                if (bd.scale() <= 0 && bd.compareTo(java.math.BigDecimal.valueOf(bd.longValue())) == 0) {
                    long v = bd.longValue();
                    if (v >= Byte.MIN_VALUE && v <= Byte.MAX_VALUE)
                        return ByteBinaryTag.byteBinaryTag((byte) v);
                    if (v >= Short.MIN_VALUE && v <= Short.MAX_VALUE)
                        return ShortBinaryTag.shortBinaryTag((short) v);
                    if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
                        return IntBinaryTag.intBinaryTag((int) v);
                    return LongBinaryTag.longBinaryTag(v);
                }
                return DoubleBinaryTag.doubleBinaryTag(bd.doubleValue());
            } else if (jsonPrimitive.isString()) {
                return StringBinaryTag.stringBinaryTag(jsonPrimitive.getAsString());
            } else if (jsonPrimitive.isBoolean()) {
                return ByteBinaryTag.byteBinaryTag(jsonPrimitive.getAsBoolean() ? (byte) 1 : (byte) 0);
            } else {
                throw new IllegalArgumentException("Unknown JSON primitive: " + jsonPrimitive);
            }
        } else if (json instanceof JsonObject) {
            CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
            for (Map.Entry<String, JsonElement> property : ((JsonObject) json).entrySet()) {
                builder.put(property.getKey(), fromJson(property.getValue()));
            }

            return builder.build();
        } else if (json instanceof JsonArray) {
            List<JsonElement> jsonArray = ((JsonArray) json).asList();

            if (jsonArray.isEmpty()) {
                return ListBinaryTag.listBinaryTag(EndBinaryTag.endBinaryTag().type(), Collections.emptyList());
            }

            BinaryTagType tagByteType = ByteBinaryTag.ZERO.type();
            BinaryTagType tagIntType = IntBinaryTag.intBinaryTag(0).type();
            BinaryTagType tagLongType = LongBinaryTag.longBinaryTag(0).type();

            BinaryTag listTag;
            BinaryTagType listType = fromJson(jsonArray.get(0)).type();
            if (listType.equals(tagByteType)) {
                byte[] bytes = new byte[jsonArray.size()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (Byte) ((JsonPrimitive) jsonArray.get(i)).getAsNumber();
                }

                listTag = ByteArrayBinaryTag.byteArrayBinaryTag(bytes);
            } else if (listType.equals(tagIntType)) {
                int[] ints = new int[jsonArray.size()];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = (Integer) ((JsonPrimitive) jsonArray.get(i)).getAsNumber();
                }

                listTag = IntArrayBinaryTag.intArrayBinaryTag(ints);
            } else if (listType.equals(tagLongType)) {
                long[] longs = new long[jsonArray.size()];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = (Long) ((JsonPrimitive) jsonArray.get(i)).getAsNumber();
                }

                listTag = LongArrayBinaryTag.longArrayBinaryTag(longs);
            } else {
                List<BinaryTag> tagItems = new ArrayList<>(jsonArray.size());

                for (JsonElement jsonEl : jsonArray) {
                    BinaryTag subTag = fromJson(jsonEl);
                    if (subTag.type() != listType) {
                        throw new IllegalArgumentException("Cannot convert mixed JsonArray to Tag");
                    }

                    tagItems.add(subTag);
                }

                listTag = ListBinaryTag.listBinaryTag(listType, tagItems);
            }

            return listTag;
        } else if (json instanceof JsonNull) {
            return EndBinaryTag.endBinaryTag();
        }

        throw new IllegalArgumentException("Unknown JSON element: " + json);
    }

}
