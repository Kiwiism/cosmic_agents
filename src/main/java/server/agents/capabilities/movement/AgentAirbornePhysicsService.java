package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

/**
 * Agent-owned seam for airborne movement integration while physics internals migrate.
 */
public final class AgentAirbornePhysicsService {
    private AgentAirbornePhysicsService() {
    }

    public static AgentAirborneStepResult stepAirborne(BotEntry entry, Character agent) {
        return switch (BotPhysicsEngine.stepAirborne(entry, agent)) {
            case WALL -> AgentAirborneStepResult.WALL;
            case CEILING -> AgentAirborneStepResult.CEILING;
            case LANDED -> AgentAirborneStepResult.LANDED;
            case CONTINUE -> AgentAirborneStepResult.CONTINUE;
        };
    }

    public static boolean canLand(BotEntry entry) {
        return AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry) == 0L;
    }
}
