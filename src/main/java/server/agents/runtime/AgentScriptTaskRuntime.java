package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import server.bots.BotEntry;

public final class AgentScriptTaskRuntime {
    private AgentScriptTaskRuntime() {
    }

    public static void tick(BotEntry entry) {
        tick(entry, AgentMovementPhysicsConfig.configuredStopDist());
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
