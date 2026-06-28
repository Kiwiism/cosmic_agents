package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotDeathStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotDeathStateRuntimeTest {
    @Test
    void entersAndClearsDeathWindow() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);

        assertEquals(6_000L, AgentBotDeathStateRuntime.deadUntilMs(entry));
        assertTrue(AgentBotDeathStateRuntime.isDead(entry));

        AgentBotDeathStateRuntime.clear(entry);

        assertEquals(0L, AgentBotDeathStateRuntime.deadUntilMs(entry));
        assertFalse(AgentBotDeathStateRuntime.isDead(entry));
    }

    @Test
    void detectsEntryIntoDeadStateOnlyOnce() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentBotDeathStateRuntime.shouldEnterDeadState(entry, 0));
        AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);
        assertFalse(AgentBotDeathStateRuntime.shouldEnterDeadState(entry, 0));
        assertFalse(AgentBotDeathStateRuntime.shouldEnterDeadState(entry, 1));
    }

    @Test
    void detectsRespawnDueAfterDeadline() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotDeathStateRuntime.enterDeadState(entry, 1_000L, 5_000L);

        assertFalse(AgentBotDeathStateRuntime.isRespawnDue(entry, 5_999L));
        assertTrue(AgentBotDeathStateRuntime.isRespawnDue(entry, 6_000L));
        assertTrue(AgentBotDeathStateRuntime.isRespawnDue(entry, 6_001L));

        AgentBotDeathStateRuntime.clear(entry);
        assertFalse(AgentBotDeathStateRuntime.isRespawnDue(entry, 10_000L));
    }
}
