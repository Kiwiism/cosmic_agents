package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickCadenceStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotTickCadenceStateRuntimeTest {
    @Test
    void resetsTickCadenceState() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setSkipDelayMs(250);
        entry.setAiTickAccumulatorMs(75);

        AgentBotTickCadenceStateRuntime.reset(entry);

        assertEquals(0, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertEquals(0, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }

    @Test
    void consumesSkipDelayByTick() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.setSkipDelayMs(150);

        assertTrue(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(50, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertTrue(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(0, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertFalse(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
    }

    @Test
    void consumesAiTicksWhenAccumulatorReachesCadence() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(50, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
        assertTrue(AgentBotTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(0, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }
}
