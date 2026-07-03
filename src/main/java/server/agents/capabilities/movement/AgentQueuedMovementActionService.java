package server.agents.capabilities.movement;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Rope;

/**
 * Agent-owned seam for queued movement actions while physics internals migrate.
 */
public final class AgentQueuedMovementActionService {
    private AgentQueuedMovementActionService() {
    }

    public static void queueDownJump(BotEntry entry, Character agent) {
        BotPhysicsEngine.queueDownJump(entry, agent);
    }

    public static void queueTopRopeEntry(BotEntry entry, Character agent, Rope rope, int y) {
        BotPhysicsEngine.queueTopRopeEntry(entry, agent, rope, y);
    }

    public static void beginDownJump(BotEntry entry, Character agent) {
        BotPhysicsEngine.beginDownJump(entry, agent);
    }

    public static void beginTopRopeEntry(BotEntry entry, Character agent) {
        BotPhysicsEngine.beginTopRopeEntry(entry, agent);
    }
}
