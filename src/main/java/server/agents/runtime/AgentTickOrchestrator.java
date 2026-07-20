package server.agents.runtime;

import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.capabilities.movement.AgentMovementSettleService;

import server.agents.runtime.AgentTickCadenceStateRuntime;
import server.agents.runtime.AgentTickStateRuntime;

/**
 * Agent-owned tick orchestration helpers over AgentRuntimeEntry-backed state.
 * Full tick dispatch now enters through AgentInteractionRuntime and shared
 * Agent tick services while Agent runtime state is centralized.
 */
public final class AgentTickOrchestrator {
    @FunctionalInterface
    public interface TickCore {
        void run(AgentRuntimeEntry entry, int leaderCharId, int agentCharId);
    }

    @FunctionalInterface
    public interface TickFailureHandler {
        void handle(AgentRuntimeEntry entry, int leaderCharId, int agentCharId, Throwable failure);
    }

    private AgentTickOrchestrator() {
    }

    public static void runGuardedTick(AgentRuntimeEntry entry,
                                      int leaderCharId,
                                      int agentCharId,
                                      TickCore tickCore,
                                      TickFailureHandler failureHandler) {
        long startedAt = AgentPerformanceMonitor.enabled() ? System.nanoTime() : 0L;
        try {
            AgentMovementSettleService.beginTick(entry);
            AgentMailboxRuntime.drain(entry);
            tickCore.run(entry, leaderCharId, agentCharId);
            AgentMovementSettleService.settleIfNeeded(entry);
            AgentEventDispatchRuntime.drain(entry);
            AgentTickFailurePolicy.resetFailures(entry);
        } catch (Throwable t) {
            failureHandler.handle(entry, leaderCharId, agentCharId, t);
        } finally {
            if (startedAt != 0L) {
                AgentPerformanceMonitor.record("tick-total", System.nanoTime() - startedAt);
            }
        }
    }

    public static boolean prepareTick(AgentRuntimeEntry entry, int movementTickMs, int aiTickMs, long tickAtMs) {
        boolean runAiTick = AgentTickCadenceStateRuntime.consumeAiTick(entry, movementTickMs, aiTickMs);
        AgentTickStateRuntime.recordTick(entry, runAiTick, tickAtMs);
        return runAiTick;
    }
}
