package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentTickStateRuntime;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentHeartbeatServiceTest {
    @Test
    void skipsHeartbeatWhenIntervalHasNotElapsed() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentTickStateRuntime.markHeartbeat(entry, 1_000L);
        AtomicInteger lastPacketUpdates = new AtomicInteger();
        AtomicInteger broadcasts = new AtomicInteger();

        boolean ran = AgentHeartbeatService.tickHeartbeat(
                entry, agent, 1_599L, 600L, ignored -> lastPacketUpdates.incrementAndGet(), ignored -> broadcasts.incrementAndGet());

        assertFalse(ran);
        assertEquals(0, lastPacketUpdates.get());
        assertEquals(0, broadcasts.get());
    }

    @Test
    void marksHeartbeatAndRunsSideEffectsWhenDue() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentTickStateRuntime.markHeartbeat(entry, 1_000L);
        AtomicInteger lastPacketUpdates = new AtomicInteger();
        AtomicInteger broadcasts = new AtomicInteger();

        boolean ran = AgentHeartbeatService.tickHeartbeat(
                entry, agent, 1_600L, 600L, ignored -> lastPacketUpdates.incrementAndGet(), ignored -> broadcasts.incrementAndGet());

        assertTrue(ran);
        assertEquals(1, lastPacketUpdates.get());
        assertEquals(1, broadcasts.get());
        assertFalse(AgentTickStateRuntime.heartbeatDue(entry, 1_600L, 600L));
    }
}
