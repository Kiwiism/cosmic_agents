package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateRuntime;

import java.awt.Point;
import java.util.function.BiPredicate;

/**
 * Agent-owned movement phase dispatch for a single movement tick.
 */
public final class AgentMovementPhaseService {
    @FunctionalInterface
    public interface ClimbTick {
        void tick(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface MoveTick {
        void tick(AgentRuntimeEntry entry, Point targetPosition);
    }

    public record MovementPhaseHooks(BiPredicate<AgentRuntimeEntry, Point> swimMap,
                                     ClimbTick climbingTick,
                                     MoveTick swimmingTick,
                                     MoveTick airborneTick,
                                     MoveTick groundedTick) {
    }

    private AgentMovementPhaseService() {
    }

    public static void tickMovementPhase(AgentRuntimeEntry entry,
                                         Point targetPosition,
                                         boolean runAiTick,
                                         MovementPhaseHooks hooks) {
        if (AgentMovementStateRuntime.climbing(entry)) {
            hooks.climbingTick().tick(entry, targetPosition, runAiTick);
        } else if (hooks.swimMap().test(entry, targetPosition) && AgentMovementStateRuntime.inAir(entry)) {
            hooks.swimmingTick().tick(entry, targetPosition);
        } else if (AgentMovementStateRuntime.inAir(entry)) {
            hooks.airborneTick().tick(entry, targetPosition);
        } else {
            hooks.groundedTick().tick(entry, targetPosition);
        }
    }
}
