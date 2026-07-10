package server.agents.plans;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentScriptTaskCoordinator {
    private AgentScriptTaskCoordinator() {
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
