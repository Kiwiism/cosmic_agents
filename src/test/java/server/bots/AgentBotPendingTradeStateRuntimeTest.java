package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPendingTradeStateRuntime;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPendingTradeStateRuntimeTest {
    @Test
    void adaptsPendingTradeActiveGuard() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertTrue(AgentBotPendingTradeStateRuntime.isIdle(entry));

        entry.pendingTradeCategory = "trash";

        assertTrue(AgentBotPendingTradeStateRuntime.hasActiveSequence(entry));
        assertFalse(AgentBotPendingTradeStateRuntime.isIdle(entry));
    }

    @Test
    void adaptsQueuedTradeRetryState() {
        BotEntry entry = new BotEntry(null, null, null);
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);
        Runnable first = () -> firstRan.set(true);
        Runnable second = () -> secondRan.set(true);

        AgentBotPendingTradeStateRuntime.queueRetry(entry, first, 10_000);
        AgentBotPendingTradeStateRuntime.queueRetry(entry, second, 5_000);

        assertTrue(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
        assertEquals(10_000, AgentBotPendingTradeStateRuntime.retryDelayMs(entry));

        AgentBotPendingTradeStateRuntime.setRetryDelayMs(entry, 0);
        Runnable retry = AgentBotPendingTradeStateRuntime.takeRetry(entry);

        assertSame(first, retry);
        assertFalse(AgentBotPendingTradeStateRuntime.hasQueuedRetry(entry));
        retry.run();
        assertTrue(firstRan.get());
        assertFalse(secondRan.get());
    }

    @Test
    void adaptsShareBudgetCapState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals((short) 5000, AgentBotPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));

        AgentBotPendingTradeStateRuntime.setShareBudget(entry, 2250);

        assertEquals(2250, AgentBotPendingTradeStateRuntime.shareBudget(entry));
        assertEquals((short) 2250, AgentBotPendingTradeStateRuntime.capShareQuantity(entry, (short) 5000));
        assertEquals(0, AgentBotPendingTradeStateRuntime.shareBudget(entry));

        AgentBotPendingTradeStateRuntime.setShareBudget(entry, 1000);
        AgentBotPendingTradeStateRuntime.clearShareBudget(entry);

        assertEquals(0, AgentBotPendingTradeStateRuntime.shareBudget(entry));
    }
}
