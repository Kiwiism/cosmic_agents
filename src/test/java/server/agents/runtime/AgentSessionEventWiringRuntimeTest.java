package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.events.AgentEventBus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentSessionEventWiringRuntimeTest {
    @Test
    void productionSubscriptionsAreRegisteredOnceAndClosedWithSession() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentEventBus bus = AgentSessionEventRuntime.bus(entry);

        assertEquals(5, bus.snapshot().subscriptions());
        assertEquals(5, AgentSessionEventRuntime.bus(entry).snapshot().subscriptions());

        AgentSessionEventRuntime.close(entry);

        assertTrue(bus.snapshot().closed());
        assertEquals(0, bus.snapshot().subscriptions());
        assertFalse(entry.capabilityStates().find(AgentSessionEventWiringState.STATE_KEY).isPresent());
    }
}
