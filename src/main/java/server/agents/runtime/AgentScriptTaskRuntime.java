package server.agents.runtime;

import server.bots.BotEntry;

public final class AgentScriptTaskRuntime {
    private AgentScriptTaskRuntime() {
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
