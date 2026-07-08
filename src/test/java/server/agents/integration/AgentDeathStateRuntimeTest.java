package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentDeathStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDeathStateRuntimeTest {
    @Test
    void entersAndClearsDeathWindow() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);

        assertEquals(6_000L, AgentDeathStateRuntime.deadUntilMs(entry));
        assertTrue(AgentDeathStateRuntime.isDead(entry));

        AgentDeathStateRuntime.clear(entry);

        assertEquals(0L, AgentDeathStateRuntime.deadUntilMs(entry));
        assertFalse(AgentDeathStateRuntime.isDead(entry));
    }

    @Test
    void detectsEntryIntoDeadStateOnlyOnce() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentDeathStateRuntime.shouldEnterDeadState(entry, 0));
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);
        assertFalse(AgentDeathStateRuntime.shouldEnterDeadState(entry, 0));
        assertFalse(AgentDeathStateRuntime.shouldEnterDeadState(entry, 1));
    }

    @Test
    void detectsRespawnDueAfterDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);

        assertFalse(AgentDeathStateRuntime.isRespawnDue(entry, 5_999L));
        assertTrue(AgentDeathStateRuntime.isRespawnDue(entry, 6_000L));
        assertTrue(AgentDeathStateRuntime.isRespawnDue(entry, 6_001L));

        AgentDeathStateRuntime.clear(entry);
        assertFalse(AgentDeathStateRuntime.isRespawnDue(entry, 10_000L));
    }
}
