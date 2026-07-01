package server.agents.runtime;

import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.function.BiPredicate;

/**
 * Agent-owned movement phase dispatch for a single movement tick.
 */
public final class AgentMovementPhaseService {
    @FunctionalInterface
    public interface ClimbTick {
        void tick(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface MoveTick {
        void tick(BotEntry entry, Point targetPosition);
    }

    public record MovementPhaseHooks(BiPredicate<BotEntry, Point> swimMap,
                                     ClimbTick climbingTick,
                                     MoveTick swimmingTick,
                                     MoveTick airborneTick,
                                     MoveTick groundedTick) {
    }

    private AgentMovementPhaseService() {
    }

    public static void tickMovementPhase(BotEntry entry,
                                         Point targetPosition,
                                         boolean runAiTick,
                                         MovementPhaseHooks hooks) {
        if (AgentBotMovementStateRuntime.climbing(entry)) {
            hooks.climbingTick().tick(entry, targetPosition, runAiTick);
        } else if (hooks.swimMap().test(entry, targetPosition) && AgentBotMovementStateRuntime.inAir(entry)) {
            hooks.swimmingTick().tick(entry, targetPosition);
        } else if (AgentBotMovementStateRuntime.inAir(entry)) {
            hooks.airborneTick().tick(entry, targetPosition);
        } else {
            hooks.groundedTick().tick(entry, targetPosition);
        }
    }
}
