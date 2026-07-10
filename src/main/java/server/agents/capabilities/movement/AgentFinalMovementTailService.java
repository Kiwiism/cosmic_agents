package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentFinalMovementTailService {
    private AgentFinalMovementTailService() {
    }

    public record Hooks(MovementCore movementCore) {
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static void stepFinalMovement(AgentRuntimeEntry entry,
                                         Point targetPosition,
                                         boolean runAiTick,
                                         Hooks hooks) {
        hooks.movementCore().step(entry, targetPosition, runAiTick);
    }
}
