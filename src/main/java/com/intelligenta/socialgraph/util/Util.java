package com.intelligenta.socialgraph.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.UUID;

/**
 * Utility methods for the application.
 */
public final class Util {

    private Util() {
        // Utility class, no instantiation
    }

    /**
     * Generate a new UUID string.
     */
    public static String UUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Calculate MD5 hash of byte array.
     */
    public static String getMD5(byte[] bytes) {
        return DigestUtils.md5Hex(bytes);
    }

    /**
     * Get current Unix timestamp in seconds.
     */
    public static String unixtime() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }
}
