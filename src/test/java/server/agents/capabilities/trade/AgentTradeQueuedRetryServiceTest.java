package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeQueuedRetryServiceTest {
    @Test
    void returnsFalseWhenNoQueuedRetryExists() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        assertFalse(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 1));
    }

    @Test
    void ticksQueuedRetryDelayBeforeRunning() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AtomicInteger runs = new AtomicInteger();
        AgentPendingTradeStateRuntime.queueRetry(entry, runs::incrementAndGet, 500);

        assertTrue(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 100));

        assertEquals(400, AgentPendingTradeStateRuntime.retryDelayMs(entry));
        assertEquals(0, runs.get());
        assertTrue(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
    }

    @Test
    void runsQueuedRetryWhenDelayExpires() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AtomicInteger runs = new AtomicInteger();
        AgentPendingTradeStateRuntime.queueRetry(entry, runs::incrementAndGet, 0);

        assertTrue(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 100));

        assertEquals(1, runs.get());
        assertFalse(AgentPendingTradeStateRuntime.hasQueuedRetry(entry));
    }
}
