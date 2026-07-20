package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEventBus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentSessionEventWiringRuntimeTest {
    @AfterEach
    void clearRolloutProperties() {
        System.clearProperty("agents.events.reactions.enabled");
        System.clearProperty("agents.events.dialogue.enabled");
        System.clearProperty("agents.events.coordination.enabled");
        System.clearProperty("agents.events.llmContext.enabled");
    }

    @Test
    void productionSubscriptionsAreRegisteredOnceAndClosedWithSession() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);

        assertEquals(16, bus.snapshot().subscriptions());
        assertEquals(16, AgentSessionEventRuntime.bus(entry).snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);

        assertTrue(bus.snapshot().closed());
        assertEquals(0, bus.snapshot().subscriptions());
        assertFalse(entry.capabilityStates().find(AgentSessionEventWiringState.STATE_KEY).isPresent());
    }

    @Test
    void optionalConsumersCanBeRolledBackIndependentlyOfMonitoring() {
        System.setProperty("agents.events.reactions.enabled", "false");
        System.setProperty("agents.events.dialogue.enabled", "false");
        System.setProperty("agents.events.coordination.enabled", "false");
        System.setProperty("agents.events.llmContext.enabled", "false");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);

        assertEquals(5, bus.snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);
    }
}
