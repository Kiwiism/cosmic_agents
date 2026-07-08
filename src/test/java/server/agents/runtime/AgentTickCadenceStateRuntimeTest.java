package server.agents.runtime;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentTickCadenceStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTickCadenceStateRuntimeTest {
    @Test
    void resetsTickCadenceState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTickCadenceStateRuntime.setSkipDelayMs(entry, 250);
        AgentTickCadenceStateRuntime.setAiTickAccumulatorMs(entry, 75);

        AgentTickCadenceStateRuntime.reset(entry);

        assertEquals(0, AgentTickCadenceStateRuntime.skipDelayMs(entry));
        assertEquals(0, AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }

    @Test
    void consumesSkipDelayByTick() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTickCadenceStateRuntime.setSkipDelayMs(entry, 150);

        assertTrue(AgentTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(50, AgentTickCadenceStateRuntime.skipDelayMs(entry));
        assertTrue(AgentTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
        assertEquals(0, AgentTickCadenceStateRuntime.skipDelayMs(entry));
        assertFalse(AgentTickCadenceStateRuntime.consumeSkipDelay(entry, 100));
    }

    @Test
    void consumesAiTicksWhenAccumulatorReachesCadence() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(50, AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
        assertTrue(AgentTickCadenceStateRuntime.consumeAiTick(entry, 50, 100));
        assertEquals(0, AgentTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }
}
