package server.agents.capabilities.movement;

import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

/**
 * Agent-owned seam for swim movement integration while physics internals migrate.
 */
public final class AgentSwimPhysicsService {
    private AgentSwimPhysicsService() {
    }

    public static void applySwimMotion(BotEntry entry) {
        BotPhysicsEngine.applySwimMotion(entry);
    }
}
