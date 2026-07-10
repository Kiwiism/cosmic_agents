package server.agents.runtime;

import server.agents.integration.AgentSchedulerGatewayRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Agent-owned bridge for delayed chat/report/status callbacks.
 */
public final class AgentSchedulerRuntime {
    private AgentSchedulerRuntime() {
    }

    public static void afterRandomDelay(int minMs, int maxMs, Runnable action) {
        afterDelay(randomDelayMs(minMs, maxMs), action);
    }

    public static void afterRandomDelay(AgentRuntimeEntry entry, int minMs, int maxMs, Runnable action) {
        afterDelay(entry, randomDelayMs(minMs, maxMs), action);
    }

    public static void afterDelay(long delayMs, Runnable action) {
        schedule(action, delayMs);
    }

    public static void afterDelay(AgentRuntimeEntry entry, long delayMs, Runnable action) {
        schedule(entry, action, delayMs);
    }

    public static ScheduledFuture<?> schedule(Runnable action, long delayMs) {
        return AgentSchedulerGatewayRuntime.scheduler().schedule(action, delayMs);
    }

    public static ScheduledFuture<?> schedule(AgentRuntimeEntry entry, Runnable action, long delayMs) {
        if (entry == null) {
            return schedule(action, delayMs);
        }
        return scheduleScoped(
                entry,
                action,
                scopedAction -> AgentSchedulerGatewayRuntime.scheduler().schedule(scopedAction, delayMs));
    }

    public static ScheduledFuture<?> scheduleScoped(
            AgentRuntimeEntry entry,
            Runnable action,
            Function<Runnable, ScheduledFuture<?>> scheduler) {
        long generation = entry.sessionGeneration();
        return entry.scheduledTaskScope().schedule(
                scheduler,
                () -> {
                    if (AgentRuntimeRegistry.isActiveSession(entry, generation)) {
                        action.run();
                    }
                });
    }

    public static boolean isCurrentSession(AgentRuntimeEntry entry) {
        return entry != null
                && AgentRuntimeRegistry.isActiveSession(entry, entry.sessionGeneration());
    }

    public static ScheduledFuture<?> register(Runnable action, long periodMs) {
        return AgentSchedulerGatewayRuntime.scheduler().register(action, periodMs);
    }

    public static long randomDelayMs(int minMs, int maxMs) {
        return minMs + ThreadLocalRandom.current().nextInt(maxMs - minMs);
    }
}
