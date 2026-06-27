package server.agents.integration;

import server.TimerManager;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned bridge for delayed chat/report/status callbacks.
 */
public final class AgentBotSchedulerRuntime {
    private AgentBotSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        afterDelay(randomDelayMs(minMs, maxMs), action);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        TimerManager.getInstance().schedule(action, delayMs);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return minMs + ThreadLocalRandom.current().nextInt(maxMs - minMs);
    }
}
