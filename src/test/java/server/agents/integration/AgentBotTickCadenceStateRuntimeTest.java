package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickCadenceStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotTickCadenceStateRuntimeTest {
    @Test
    void resetsTickCadenceState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotTickCadenceStateRuntime.setSkipDelayMs(entry, 250);
        AgentBotTickCadenceStateRuntime.setAiTickAccumulatorMs(entry, 75);

        AgentBotTickCadenceStateRuntime.reset(entry);

        assertEquals(0, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertEquals(0, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }

    @Test
    void consumesSkipDelayByTick() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotTickCadenceStateRuntime.setSkipDelayMs(entry, 150);

        assertTrue(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(50, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertTrue(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(0, AgentBotTickCadenceStateRuntime.skipDelayMs(entry));
        assertFalse(AgentBotTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
    }

    @Test
    void consumesAiTicksWhenAccumulatorReachesCadence() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(50, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
        assertTrue(AgentBotTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(0, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }
}
