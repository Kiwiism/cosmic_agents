package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;

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
        boolean ground(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface MovementProfileRefresher {
        void refresh(BotEntry entry);
    }

    @FunctionalInterface
    public interface MovementCore {
        void step(BotEntry entry, Point targetPosition, boolean runAiTick);
    }

    public static void tickStandaloneMoveTarget(BotEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                Hooks hooks) {
        if (hooks.mapChangeGrounder().ground(entry, agent)) {
            return;
        }

        hooks.movementProfileRefresher().refresh(entry);
        hooks.movementCore().step(entry, AgentBotMoveTargetStateRuntime.moveTarget(entry), runAiTick);
    }
}
