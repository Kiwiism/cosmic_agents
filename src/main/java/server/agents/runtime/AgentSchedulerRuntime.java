package server.agents.runtime;

import server.TimerManager;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent-owned bridge for delayed chat/report/status callbacks.
 */
public final class AgentSchedulerRuntime {
    private AgentSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        afterDelay(randomDelayMs(minMs, maxMs), action);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        schedule(action, delayMs);
    }

    public static ScheduledFuture<?> schedule(Runnable action, long delayMs) {
        return TimerManager.getInstance().schedule(action, delayMs);
    }

    public static ScheduledFuture<?> register(Runnable action, long periodMs) {
        return TimerManager.getInstance().register(action, periodMs);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return minMs + ThreadLocalRandom.current().nextInt(maxMs - minMs);
    }
}
