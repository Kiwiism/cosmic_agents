package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentScrollReactionStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScrollReactionStateRuntimeTest {
    @Test
    void adaptsCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentScrollReactionStateRuntime.isOnCooldown(entry, 100));

        AgentScrollReactionStateRuntime.startCooldown(entry, 100, 250);

        assertTrue(AgentScrollReactionStateRuntime.isOnCooldown(entry, 349));
        assertFalse(AgentScrollReactionStateRuntime.isOnCooldown(entry, 350));
    }

    @Test
    void adaptsLoadDecayState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1.0, AgentScrollReactionStateRuntime.recordReactionLoad(entry, 1_000, 60_000), 0.0001);
        double decayed = AgentScrollReactionStateRuntime.recordReactionLoad(entry, 61_000, 60_000);

        assertTrue(decayed > 1.0);
        assertTrue(decayed < 2.0);
        assertEquals(61_000, entry.scrollReactionState().lastObservedAtMs());
    }

    @Test
    void adaptsScrollerStreakState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1, AgentScrollReactionStateRuntime.updateReactionStreak(entry, 7, true, 1_000, 45_000, 60_000));
        assertEquals(2, AgentScrollReactionStateRuntime.updateReactionStreak(entry, 7, true, 2_000, 45_000, 60_000));
        assertEquals(1, AgentScrollReactionStateRuntime.updateReactionStreak(entry, 7, false, 3_000, 45_000, 60_000));
    }
}
