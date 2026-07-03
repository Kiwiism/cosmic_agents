package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.bots.BotEntry;

public final class AgentActionLockPhysicsRuntime {
    private AgentActionLockPhysicsRuntime() {
    }

    public static boolean tickActionLocked(BotEntry entry) {
        return AgentActionLockPhysicsService.tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                locked -> AgentMovementPhaseDispatchService.tickSwimming(locked, null),
                locked -> AgentMovementPhaseDispatchService.tickAirborne(locked, null),
                locked -> AgentMovementPhaseDispatchService.tickGrounded(locked, null));
    }
}
