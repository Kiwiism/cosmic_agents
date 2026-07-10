package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementTimers;
import server.agents.capabilities.movement.AgentStuckDetectionService;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementRecoveryService;

public final class AgentStuckDetectionRuntime {
    private AgentStuckDetectionRuntime() {
    }

    public static void tickStuckDetection(AgentRuntimeEntry entry, boolean enableUnstuck) {
        AgentStuckDetectionService.tickStuckDetection(
                entry,
                new AgentStuckDetectionService.StuckDetectionHooks(
                        AgentMovementTimers::tickDown,
                        AgentMovementRecoveryService::tickUnstuck,
                        AgentMovementPhysicsConfig.configuredMovementTickMs(),
                        enableUnstuck));
    }
}
