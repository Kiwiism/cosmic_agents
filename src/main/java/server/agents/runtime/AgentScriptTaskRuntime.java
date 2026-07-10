package server.agents.runtime;

import server.agents.plans.AgentScriptTaskExecutionService;
import server.agents.plans.AgentScriptTaskTickService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

public final class AgentScriptTaskRuntime {
    private AgentScriptTaskRuntime() {
    }

    public static void tick(AgentRuntimeEntry entry) {
        tick(entry, AgentMovementPhysicsConfig.configuredStopDist());
    }

    public static void tick(AgentRuntimeEntry entry, int normalMoveArrivalDistance) {
        AgentScriptTaskTickService.tick(
                entry,
                AgentScriptTaskExecutionService::start,
                (taskEntry, task) -> AgentScriptTaskExecutionService.isComplete(
                        taskEntry,
                        task,
                        normalMoveArrivalDistance));
    }
}
