package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.bots.BotEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTradeQueuedRetryServiceTest {
    @Test
    void returnsFalseWhenNoQueuedRetryExists() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);

        assertFalse(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 1));
    }

    @Test
    void ticksQueuedRetryDelayBeforeRunning() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);
        AtomicInteger runs = new AtomicInteger();
        AgentBotPendingTradeStateRuntime.queueRetry(entry, runs::incrementAndGet, 500);

        assertTrue(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 100));

        assertEquals(400, AgentBotPendingTradeStateRuntime.retryDelayMs(entry));
        assertEquals(0, runs.get());
        assertTrue(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
    }

    @Test
    void runsQueuedRetryWhenDelayExpires() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);
        AtomicInteger runs = new AtomicInteger();
        AgentBotPendingTradeStateRuntime.queueRetry(entry, runs::incrementAndGet, 0);

        assertTrue(AgentTradeQueuedRetryService.tickQueuedRetry(entry, remaining -> remaining - 100));

        assertEquals(1, runs.get());
        assertFalse(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
    }
}
