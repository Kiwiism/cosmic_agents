package server.agents.runtime;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentScriptTaskRuntime {
    private AgentScriptTaskRuntime() {
    }

    public static void tick(BotEntry entry) {
        tick(entry, BotMovementManager.configuredStopDist());
    }

    public static void tick(BotEntry entry, int normalMoveArrivalDistance) {
        AgentScriptTaskTickService.tick(
                entry,
                AgentScriptTaskExecutionService::start,
                (taskEntry, task) -> AgentScriptTaskExecutionService.isComplete(
                        taskEntry,
                        task,
                        normalMoveArrivalDistance));
    }
}
