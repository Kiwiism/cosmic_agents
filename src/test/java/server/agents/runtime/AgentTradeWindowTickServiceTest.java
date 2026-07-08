package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.Trade;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeWindowTickServiceTest {
    @Test
    void fallsThroughWhenTradeWindowIsNotOpen() {
        Character agent = mock(Character.class);
        when(agent.getTrade()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AtomicInteger physicsTicks = new AtomicInteger();

        boolean consumed = AgentTradeWindowTickService.tickIfTradeWindowOpen(
                entry,
                agent,
                (tickEntry, tickAgent) -> physicsTicks.incrementAndGet());

        assertFalse(consumed);
        assertEquals(0, physicsTicks.get());
    }

    @Test
    void consumesTickAndRunsPhysicsWhenTradeWindowIsOpen() {
        Character agent = mock(Character.class);
        when(agent.getTrade()).thenReturn(mock(Trade.class));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AtomicInteger physicsTicks = new AtomicInteger();

        boolean consumed = AgentTradeWindowTickService.tickIfTradeWindowOpen(
                entry,
                agent,
                (tickEntry, tickAgent) -> physicsTicks.incrementAndGet());

        assertTrue(consumed);
        assertEquals(1, physicsTicks.get());
    }
}
