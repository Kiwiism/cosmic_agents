package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotScrollReactionStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotScrollReactionStateRuntimeTest {
    @Test
    void adaptsCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotScrollReactionStateRuntime.isOnCooldown(entry, 100));

        AgentBotScrollReactionStateRuntime.startCooldown(entry, 100, 250);

        assertTrue(AgentBotScrollReactionStateRuntime.isOnCooldown(entry, 349));
        assertFalse(AgentBotScrollReactionStateRuntime.isOnCooldown(entry, 350));
    }

    @Test
    void adaptsLoadDecayState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1.0, AgentBotScrollReactionStateRuntime.recordReactionLoad(entry, 1_000, 60_000), 0.0001);
        double decayed = AgentBotScrollReactionStateRuntime.recordReactionLoad(entry, 61_000, 60_000);

        assertTrue(decayed > 1.0);
        assertTrue(decayed < 2.0);
        assertEquals(61_000, entry.scrollReactionState().lastObservedAtMs());
    }

    @Test
    void adaptsScrollerStreakState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(1, AgentBotScrollReactionStateRuntime.updateReactionStreak(entry, 7, true, 1_000, 45_000, 60_000));
        assertEquals(2, AgentBotScrollReactionStateRuntime.updateReactionStreak(entry, 7, true, 2_000, 45_000, 60_000));
        assertEquals(1, AgentBotScrollReactionStateRuntime.updateReactionStreak(entry, 7, false, 3_000, 45_000, 60_000));
    }
}
