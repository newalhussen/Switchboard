package dev.switchboard;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Flag evaluation. Pure and deterministic: the same flag and user always land in the same bucket, so a user
 * never flips between on and off, and raising the rollout percentage only ever adds users.
 */
public final class Evaluator {
    private Evaluator() {}

    public static final int BUCKETS = 10_000; // 0.01% granularity

    public enum Reason { DISABLED, ROLLOUT_ALL, ROLLOUT_NONE, ROLLOUT_IN, ROLLOUT_OUT, USER_REQUIRED }

    public record Result(boolean value, Reason reason) {}

    /** Stable bucket in [0, 10000) derived from SHA-256(flagKey ":" userId). */
    public static int bucket(String flagKey, String userId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
            long v = Integer.toUnsignedLong(ByteBuffer.wrap(h, 0, 4).getInt());
            return (int) (v % BUCKETS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Result evaluate(String flagKey, boolean enabled, int rolloutPercentage, String userId) {
        if (!enabled) return new Result(false, Reason.DISABLED);
        if (rolloutPercentage >= 100) return new Result(true, Reason.ROLLOUT_ALL);
        if (rolloutPercentage <= 0) return new Result(false, Reason.ROLLOUT_NONE);
        if (userId == null || userId.isBlank()) return new Result(false, Reason.USER_REQUIRED);
        boolean in = bucket(flagKey, userId) < rolloutPercentage * (BUCKETS / 100);
        return new Result(in, in ? Reason.ROLLOUT_IN : Reason.ROLLOUT_OUT);
    }
}
