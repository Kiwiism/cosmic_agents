package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentMovementPhaseDispatchService {
    private AgentMovementPhaseDispatchService() {
    }

    public static void tickClimbing(AgentRuntimeEntry entry, Point targetPos, boolean runAiTick) {
        AgentClimbMovementService.tickClimbing(entry, targetPos, runAiTick);
    }

    public static void tickSwimming(AgentRuntimeEntry entry, Point targetPos) {
        AgentSwimMovementService.tickSwimming(entry, targetPos);
    }

    public static void tickAirborne(AgentRuntimeEntry entry, Point targetPos) {
        AgentAirborneMovementService.tickAirborne(asBotEntry(entry), targetPos);
    }

    public static void tickGrounded(AgentRuntimeEntry entry, Point targetPos) {
        AgentGroundMovementRuntimeService.tickGrounded(asBotEntry(entry), targetPos);
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        if (entry instanceof BotEntry botEntry) {
            return botEntry;
        }
        throw new IllegalArgumentException("Legacy movement phase dispatch requires BotEntry compatibility shell");
    }
}
