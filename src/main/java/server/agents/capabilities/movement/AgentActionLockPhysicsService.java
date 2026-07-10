package server.agents.capabilities.movement;

import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AgentActionLockPhysicsService {
    private AgentActionLockPhysicsService() {
    }

    public static boolean tickActionLocked(AgentRuntimeEntry entry) {
        return tickActionLocked(
                entry,
                AgentMapEnvironmentService::isSwimMap,
                ignored -> AgentMovementPhaseDispatchService.tickSwimming(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickAirborne(entry, null),
                ignored -> AgentMovementPhaseDispatchService.tickGrounded(entry, null));
    }

    public static boolean tickActionLocked(AgentRuntimeEntry entry,
                                           Predicate<AgentRuntimeEntry> swimMap,
                                           Consumer<AgentRuntimeEntry> swimmingTick,
                                           Consumer<AgentRuntimeEntry> airborneTick,
                                           Consumer<AgentRuntimeEntry> groundedTick) {
        if (!AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return false;
        }
        if (swimMap.test(entry)
                && AgentMovementStateRuntime.inAir(entry)
                && !AgentMovementStateRuntime.climbing(entry)) {
            swimmingTick.accept(entry);
        } else if (AgentMovementStateRuntime.inAir(entry)) {
            airborneTick.accept(entry);
        } else if (!AgentMovementStateRuntime.climbing(entry)) {
            groundedTick.accept(entry);
        }
        return true;
    }
}
