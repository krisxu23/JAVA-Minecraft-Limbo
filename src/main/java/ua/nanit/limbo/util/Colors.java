package ua.nanit.limbo.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Colors {
    private static final char SECTION_SIGN = '\u00A7';
    private static final char AMPERSAND = '&';

    public static String of(String text) {
        if (text == null) return null;
        return text.replace("&", SECTION_SIGN + "");
    }

    public static Component parse(String json) {
        if (json == null || json.isEmpty()) return Component.text("");
        try {
            return GsonComponentSerializer.gson().deserialize(json);
        } catch (Exception e) {
            return Component.text(json.replace(AMPERSAND, SECTION_SIGN));
        }
    }

    public static String toJson(Component component) {
        if (component == null) return "{}";
        try {
            return GsonComponentSerializer.gson().serialize(component);
        } catch (Exception e) {
            return "{}";
        }
    }
}
