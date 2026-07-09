package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeMesoAddServiceTest {
    @Test
    void noPendingMesoDoesNothing() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicInteger mesosSet = new AtomicInteger(-1);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        boolean handled = AgentTradeMesoAddService.handlePendingMeso(
                entry,
                agent,
                mesosSet::set,
                () -> cancelled.set(true),
                () -> 500);

        assertFalse(handled);
        assertFalse(cancelled.get());
        assertEquals(-1, mesosSet.get());
    }

    @Test
    void insufficientMesoCancels() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicInteger mesosSet = new AtomicInteger(-1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 1_000);
        when(agent.getMeso()).thenReturn(999);

        boolean handled = AgentTradeMesoAddService.handlePendingMeso(
                entry,
                agent,
                mesosSet::set,
                () -> cancelled.set(true),
                () -> 500);

        assertTrue(handled);
        assertTrue(cancelled.get());
        assertEquals(-1, mesosSet.get());
        assertFalse(AgentPendingTradeStateRuntime.mesoAdded(entry));
    }

    @Test
    void enoughMesoAddsMesoAndSetsDelay() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        AtomicInteger mesosSet = new AtomicInteger(-1);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentTradeStateService.initializeBatch(entry, java.util.List.of(), 1_000);
        when(agent.getMeso()).thenReturn(1_000);

        boolean handled = AgentTradeMesoAddService.handlePendingMeso(
                entry,
                agent,
                mesosSet::set,
                () -> cancelled.set(true),
                () -> 550);

        assertTrue(handled);
        assertFalse(cancelled.get());
        assertEquals(1_000, mesosSet.get());
        assertTrue(AgentPendingTradeStateRuntime.mesoAdded(entry));
        org.junit.jupiter.api.Assertions.assertEquals(550, AgentPendingTradeStateRuntime.timerMs(entry));
    }
}
