package server.agents.capabilities.movement;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

import java.awt.Point;

/**
 * Agent-owned seam for combat-driven knockback movement.
 */
public final class AgentKnockbackMovementService {
    private AgentKnockbackMovementService() {
    }

    public static void beginKnockback(BotEntry entry, Character agent, Point position, float initialVelocityY, int airVelocityX) {
        BotPhysicsEngine.beginKnockback(entry, agent, position, initialVelocityY, airVelocityX);
    }

    public static void applyAirKnockback(BotEntry entry, Character agent, int airVelocityX) {
        BotPhysicsEngine.applyAirKnockback(entry, agent, airVelocityX);
    }
}
