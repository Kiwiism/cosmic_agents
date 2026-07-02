package server.agents.runtime;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentActionLockPhysicsRuntime {
    private AgentActionLockPhysicsRuntime() {
    }

    public static boolean tickActionLocked(BotEntry entry) {
        return AgentActionLockPhysicsService.tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                locked -> BotMovementManager.tickSwimming(locked, null),
                locked -> BotMovementManager.tickAirborne(locked, null),
                locked -> BotMovementManager.tickGrounded(locked, null));
    }
}
