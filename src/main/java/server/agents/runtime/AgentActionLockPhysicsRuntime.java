package server.agents.runtime;

import server.agents.capabilities.movement.AgentActionLockPhysicsService;
import server.agents.capabilities.movement.AgentMapEnvironmentService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;

public final class AgentActionLockPhysicsRuntime {
    private AgentActionLockPhysicsRuntime() {
    }

    public static boolean tickActionLocked(AgentRuntimeEntry entry) {
        return AgentActionLockPhysicsService.tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                ignored -> AgentMovementPhaseDispatchService.tickSwimming(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickAirborne(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickGrounded(entry, null));
    }
}
