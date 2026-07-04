package server.agents.capabilities.trade;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTradeRetryStateTest {
    @Test
    void queuesOnlyFirstRetryUntilTaken() {
        AgentTradeRetryState state = new AgentTradeRetryState();
        AtomicBoolean firstRan = new AtomicBoolean(false);
        AtomicBoolean secondRan = new AtomicBoolean(false);
        Runnable first = () -> firstRan.set(true);
        Runnable second = () -> secondRan.set(true);

        state.queueRetry(first, 10_000);
        state.queueRetry(second, 5_000);

        assertTrue(state.hasRetry());
        assertEquals(10_000, state.delayMs());

        state.setDelayMs(0);
        Runnable retry = state.takeRetry();

        assertSame(first, retry);
        assertFalse(state.hasRetry());
        retry.run();
        assertTrue(firstRan.get());
        assertFalse(secondRan.get());
    }

    @Test
    void directSetPreservesCompatibilityReplacementSemantics() {
        AgentTradeRetryState state = new AgentTradeRetryState();
        Runnable first = () -> {
        };
        Runnable second = () -> {
        };

        state.setRetry(first);
        state.setRetry(second);

        assertSame(second, state.retry());
        assertSame(second, state.takeRetry());
        assertFalse(state.hasRetry());
    }
}
