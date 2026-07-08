package server.agents.runtime;

import client.Character;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * Agent-owned ownerless/offline-leader tick branch.
 */
public final class AgentOwnerlessTickService {
    private AgentOwnerlessTickService() {
    }

    public static void tickOwnerless(AgentRuntimeEntry entry,
                                     Character agent,
                                     boolean runAiTick,
                                     BiPredicate<AgentRuntimeEntry, Character> groundAfterMapChange,
                                     OwnerlessMoveTick standaloneMoveTick,
                                     BooleanSupplier idleTick) {
        AgentModeStateRuntime.setFollowing(entry, false);
        if (groundAfterMapChange.test(entry, agent)) {
            return;
        }
        if (AgentMoveTargetStateRuntime.hasMoveTarget(entry)) {
            standaloneMoveTick.tick(entry, agent, runAiTick);
        } else {
            idleTick.getAsBoolean();
        }
    }

    @FunctionalInterface
    public interface OwnerlessMoveTick {
        void tick(AgentRuntimeEntry entry, Character agent, boolean runAiTick);
    }
}
