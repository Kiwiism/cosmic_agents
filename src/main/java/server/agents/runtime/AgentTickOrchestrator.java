package server.agents.runtime;

import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;

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
            tickCore.run(entry, leaderCharId, agentCharId);
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
        boolean runAiTick = AgentBotTickCadenceStateRuntime.consumeAiTick(entry, movementTickMs, aiTickMs);
        AgentBotTickStateRuntime.recordTick(entry, runAiTick, tickAtMs);
        return runAiTick;
    }
}
