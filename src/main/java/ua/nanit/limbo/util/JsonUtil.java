package ua.nanit.limbo.util;

/**
 * Shared JSON utility methods — consolidated from PlayerSimulator, TunnelService,
 * and NezhaService to eliminate duplicate escape/format logic.
 */
public final class JsonUtil {

    private JsonUtil() {}

    /**
     * Escape a string for safe inclusion in a JSON string value.
     * Handles backslash, double-quote, all control characters.
     */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Build a simple one-key JSON object: {"key":"escapedValue"}.
     */
    public static String object(String key, String value) {
        return "{\"" + escape(key) + "\":\"" + escape(value) + "\"}";
    }
}
