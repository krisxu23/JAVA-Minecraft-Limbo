package ua.nanit.limbo.util;

import java.security.MessageDigest;
import java.util.UUID;

public class UuidUtil {
    public static UUID getOfflineModeUuid(String username) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(("OfflinePlayer:" + username).getBytes());
            byte[] digest = md.digest();
            digest[6] &= 0x0F;
            digest[6] |= 0x30;
            digest[8] &= 0x3F;
            digest[8] |= 0x80;
            return new UUID(
                bytesToLong(digest, 0),
                bytesToLong(digest, 8)
            );
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    public static UUID fromString(String str) {
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }

    private static long bytesToLong(byte[] bytes, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[offset + i] & 0xFF);
        }
        return result;
    }
}
