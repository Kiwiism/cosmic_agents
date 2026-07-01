package server.agents.runtime;

import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.bots.BotEntry;

/**
 * Agent-owned tick orchestration helpers over temporary BotEntry-backed state.
 * Full tick dispatch remains in BotManager while reconstruction proceeds.
 */
public final class AgentTickOrchestrator {
    private AgentTickOrchestrator() {
    }

    public static boolean prepareTick(BotEntry entry, int movementTickMs, int aiTickMs, long tickAtMs) {
        boolean runAiTick = AgentBotTickCadenceStateRuntime.consumeAiTick(entry, movementTickMs, aiTickMs);
        AgentBotTickStateRuntime.recordTick(entry, runAiTick, tickAtMs);
        return runAiTick;
    }
}
