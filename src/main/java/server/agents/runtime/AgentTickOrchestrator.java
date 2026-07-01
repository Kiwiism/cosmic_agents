package server.agents.runtime;

import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.bots.BotEntry;

/**
 * Agent-owned tick orchestration helpers over temporary BotEntry-backed state.
 * Full tick dispatch remains in BotManager while reconstruction proceeds.
 */
public final class AgentTickOrchestrator {
    @FunctionalInterface
    public interface TickCore {
        void run(BotEntry entry, int leaderCharId, int agentCharId);
    }

    @FunctionalInterface
    public interface TickFailureHandler {
        void handle(BotEntry entry, int leaderCharId, int agentCharId, Throwable failure);
    }

    private AgentTickOrchestrator() {
    }

    public static void runGuardedTick(BotEntry entry,
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

    public static boolean prepareTick(BotEntry entry, int movementTickMs, int aiTickMs, long tickAtMs) {
        boolean runAiTick = AgentBotTickCadenceStateRuntime.consumeAiTick(entry, movementTickMs, aiTickMs);
        AgentBotTickStateRuntime.recordTick(entry, runAiTick, tickAtMs);
        return runAiTick;
    }
}
