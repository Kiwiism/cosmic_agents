package client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HpMpPoolPolicyTest {
    @Test
    void passiveRemovalUsesTheRawPoolInsteadOfTheVisibleCap() {
        int rawHp = 30_200;

        assertEquals(30_000, HpMpPoolPolicy.visibleValue(rawHp, 50));
        rawHp = HpMpPoolPolicy.applyDelta(rawHp, -400, 50);

        assertEquals(29_800, rawHp);
        assertEquals(29_800, HpMpPoolPolicy.visibleValue(rawHp, 50));
    }

    @Test
    void poolsCannotFallBelowTheirBaseMinimums() {
        assertEquals(50, HpMpPoolPolicy.applyDelta(60, -1_000, 50));
        assertEquals(5, HpMpPoolPolicy.applyDelta(10, -1_000, 5));
    }
}
