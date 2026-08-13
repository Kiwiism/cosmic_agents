package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatPlatformBatchStateTest {
    @Test
    void retainsOnlyNearbyMonstersInTheKnownPlatformRegion() {
        AgentCombatPlatformBatchState state = new AgentCombatPlatformBatchState();
        state.begin(100, "hunt", 7, new Point(100, 200), 4, 1_000L, 30_000L);

        assertTrue(state.includes(100, "hunt", 7,
                new Point(600, 210), 1_001L, 650, 70));
        assertFalse(state.includes(100, "hunt", 8,
                new Point(110, 205), 1_001L, 650, 70));
        assertFalse(state.includes(100, "hunt", 7,
                new Point(751, 200), 1_001L, 650, 70));
    }

    @Test
    void fallsBackToHeightBandWithoutKnownRegions() {
        AgentCombatPlatformBatchState state = new AgentCombatPlatformBatchState();
        state.begin(100, "hunt", -1, new Point(100, 200), 3, 1_000L, 30_000L);

        assertTrue(state.includes(100, "hunt", -1,
                new Point(300, 265), 1_001L, 650, 70));
        assertFalse(state.includes(100, "hunt", -1,
                new Point(300, 271), 1_001L, 650, 70));
    }

    @Test
    void authoritativeKillsAndExpiryReleaseTheBatch() {
        AgentCombatPlatformBatchState state = new AgentCombatPlatformBatchState();
        state.begin(100, "hunt", 7, new Point(100, 200), 2, 1_000L, 30_000L);

        state.killed(100, "hunt", 1_100L);
        assertTrue(state.active(100, "hunt", 1_100L));
        state.killed(100, "hunt", 1_200L);
        assertFalse(state.active(100, "hunt", 1_200L));

        state.begin(100, "hunt", 7, new Point(100, 200), 2, 2_000L, 100L);
        assertFalse(state.active(100, "hunt", 2_100L));
    }

    @Test
    void mapOrObjectiveChangeDropsTheOldBatch() {
        AgentCombatPlatformBatchState state = new AgentCombatPlatformBatchState();
        state.begin(100, "hunt-a", 7, new Point(100, 200), 3, 1_000L, 30_000L);

        assertFalse(state.active(100, "hunt-b", 1_001L));
        state.begin(100, "hunt-a", 7, new Point(100, 200), 3, 2_000L, 30_000L);
        assertFalse(state.active(101, "hunt-a", 2_001L));
    }
}
