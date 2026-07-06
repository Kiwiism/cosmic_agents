package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.bots.BotEntry;

public final class AgentActionLockPhysicsRuntime {
    private AgentActionLockPhysicsRuntime() {
    }

    public static boolean tickActionLocked(AgentRuntimeEntry entry) {
        return AgentActionLockPhysicsService.tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                ignored -> AgentMovementPhaseDispatchService.tickSwimming(asBotEntry(entry), null),
                ignored -> AgentMovementPhaseDispatchService.tickAirborne(asBotEntry(entry), null),
                ignored -> AgentMovementPhaseDispatchService.tickGrounded(asBotEntry(entry), null));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        if (entry instanceof BotEntry botEntry) {
            return botEntry;
        }
        throw new IllegalArgumentException("Legacy action-lock physics runtime requires BotEntry compatibility shell");
    }
}
