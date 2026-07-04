package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;

/**
 * Agent-owned seam for grounded movement physics while internals migrate.
 */
public final class AgentGroundPhysicsService {
    private AgentGroundPhysicsService() {
    }

    public static Foothold syncAndDetectGround(BotEntry entry, Character agent) {
        return BotPhysicsEngine.syncAndDetectGround(entry, agent);
    }

    public static AgentGroundMotion applyGroundMotion(BotEntry entry, Character agent, Foothold foothold) {
        BotPhysicsEngine.GroundMotion motion = BotPhysicsEngine.applyGroundMotion(entry, agent, foothold);
        return new AgentGroundMotion(motion.stepX(), motion.lostGround());
    }

    public static void stopGroundMotion(BotEntry entry) {
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
    }

    public static int velocityFromDeltaX(double deltaX) {
        return AgentMovementKinematicsService.velocityFromDeltaX(deltaX);
    }
}
