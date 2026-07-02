package server.agents.runtime;

import server.bots.BotEntry;

import java.awt.Point;

public final class AgentFinalMovementTailService {
    private AgentFinalMovementTailService() {
    }

    public record Hooks(MovementCore movementCore) {
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static void stepFinalMovement(BotEntry entry,
                                         Point targetPosition,
                                         boolean runAiTick,
                                         Hooks hooks) {
        hooks.movementCore().step(entry, targetPosition, runAiTick);
    }
}
