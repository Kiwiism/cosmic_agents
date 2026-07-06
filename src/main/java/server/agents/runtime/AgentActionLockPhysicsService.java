package server.agents.runtime;

import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;

import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AgentActionLockPhysicsService {
    private AgentActionLockPhysicsService() {
    }

    public static boolean tickActionLocked(AgentRuntimeEntry entry,
                                           Predicate<AgentRuntimeEntry> swimMap,
                                           Consumer<AgentRuntimeEntry> swimmingTick,
                                           Consumer<AgentRuntimeEntry> airborneTick,
                                           Consumer<AgentRuntimeEntry> groundedTick) {
        if (!AgentBotCombatCooldownStateRuntime.hasAttackCooldown(entry)) {
            return false;
        }
        if (swimMap.test(entry)
                && AgentBotMovementStateRuntime.inAir(entry)
                && !AgentBotMovementStateRuntime.climbing(entry)) {
            swimmingTick.accept(entry);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            airborneTick.accept(entry);
        } else if (!AgentBotMovementStateRuntime.climbing(entry)) {
            groundedTick.accept(entry);
        }
        return true;
    }
}
