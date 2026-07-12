package server.partner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileEffectTimingTest {
    @Test
    void preservesRemainingDurationAndRoundsCooldownUp() {
        assertEquals(2_501L, ProfileEffectTiming.remainingDurationMs(1_000L, 5_000L, 3_499L));
        assertEquals(3, ProfileEffectTiming.remainingDurationSecondsCeiling(1_000L, 5_000L, 3_499L));
    }

    @Test
    void expiredAndOverflowingEffectsAreSafe() {
        assertEquals(0L, ProfileEffectTiming.remainingDurationMs(1_000L, 500L, 2_000L));
        assertEquals(Integer.MAX_VALUE,
                ProfileEffectTiming.remainingDurationSecondsCeiling(Long.MAX_VALUE - 50L, 1_000L, 0L));
    }
}
