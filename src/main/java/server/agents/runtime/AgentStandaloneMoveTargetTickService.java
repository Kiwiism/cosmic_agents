package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentMoveTargetStateRuntime;

import java.awt.Point;

public final class AgentStandaloneMoveTargetTickService {
    private AgentStandaloneMoveTargetTickService() {
    }

    public record Hooks(MapChangeGrounder mapChangeGrounder,
                        MovementProfileRefresher movementProfileRefresher,
                        MovementCore movementCore) {
    }

    @FunctionalInterface
    public interface MapChangeGrounder {
        boolean ground(AgentRuntimeEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementProfileRefresher {
        void refresh(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static void tickStandaloneMoveTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                Hooks hooks) {
        if (hooks.mapChangeGrounder().ground(entry, agent)) {
            return;
        }

        hooks.movementProfileRefresher().refresh(entry);
        hooks.movementCore().step(entry, AgentMoveTargetStateRuntime.moveTarget(entry), runAiTick);
    }
}
