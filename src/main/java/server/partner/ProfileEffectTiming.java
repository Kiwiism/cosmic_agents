package server.partner;

/** Overflow-safe remaining-duration calculations shared by profile refresh adapters. */
public final class ProfileEffectTiming {
    private ProfileEffectTiming() {
    }

    public static long remainingDurationMs(long startedAtMs, long durationMs, long nowMs) {
        if (durationMs <= 0L) {
            return 0L;
        }
        long expiresAt;
        try {
            expiresAt = Math.addExact(startedAtMs, durationMs);
        } catch (ArithmeticException overflow) {
            expiresAt = Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt - Math.min(nowMs, expiresAt));
    }

    public static int remainingDurationSecondsCeiling(long startedAtMs, long durationMs, long nowMs) {
        long remainingMs = remainingDurationMs(startedAtMs, durationMs, nowMs);
        long seconds = remainingMs / 1_000L + (remainingMs % 1_000L == 0L ? 0L : 1L);
        return (int) Math.min(Integer.MAX_VALUE, seconds);
    }
}
