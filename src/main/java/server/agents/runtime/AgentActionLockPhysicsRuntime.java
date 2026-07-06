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
                ignored -> AgentMovementPhaseDispatchService.tickSwimming(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickAirborne(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickGrounded(entry, null));
    }
}
