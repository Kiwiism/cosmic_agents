package server.agents.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentSchedulerGatewayRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Agent-owned bridge for delayed chat/report/status callbacks.
 */
public final class AgentSchedulerRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentSchedulerRuntime.class);

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
                        dispatchScopedAction(entry, action);
                    }
                });
    }

    private static void dispatchScopedAction(AgentRuntimeEntry entry, Runnable action) {
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            action.run();
            return null;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                log.debug("Agent delayed action was not delivered for session {}",
                        entry.sessionGeneration(), failure);
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
