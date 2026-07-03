package server.agents.capabilities.movement;

import server.bots.BotEntry;
import server.bots.BotMovementManager;

import java.awt.Point;

public final class AgentMovementPhaseDispatchService {
    private AgentMovementPhaseDispatchService() {
    }

    public static void tickClimbing(BotEntry entry, Point targetPos, boolean runAiTick) {
        AgentClimbMovementService.tickClimbing(entry, targetPos, runAiTick);
    }

    public static void tickSwimming(BotEntry entry, Point targetPos) {
        AgentSwimMovementService.tickSwimming(entry, targetPos);
    }

    public static void tickAirborne(BotEntry entry, Point targetPos) {
        AgentAirborneMovementService.tickAirborne(entry, targetPos);
    }

    public static void tickGrounded(BotEntry entry, Point targetPos) {
        BotMovementManager.tickGrounded(entry, targetPos);
    }
}
