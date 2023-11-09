package dev.switchboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** SDK key helpers. Keys are shown once; only a SHA-256 hash is stored. */
public final class Keys {
    private Keys() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(String environment) {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return "sb_" + environment + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String prefix(String key) {
        return key.substring(0, Math.min(key.length(), 12)) + "…";
    }

    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
