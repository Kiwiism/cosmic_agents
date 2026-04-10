package server.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotMovementProfileTest {
    @Test
    void shouldBucketStatsDownToNearestFivePointStep() {
        BotMovementProfile profile = new BotMovementProfile(109, 117);

        assertEquals(105, profile.totalSpeedStat());
        assertEquals(115, profile.totalJumpStat());
    }

    @Test
    void shouldLeaveExactFivePointBucketsUnchanged() {
        BotMovementProfile profile = new BotMovementProfile(105, 120);

        assertEquals(105, profile.totalSpeedStat());
        assertEquals(120, profile.totalJumpStat());
    }
}
