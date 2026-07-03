package server.agents.capabilities.movement;

import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

/**
 * Agent-owned movement snapshot seam while physics internals migrate.
 */
public final class AgentMovementSnapshotService {
    private AgentMovementSnapshotService() {
    }

    public static AgentMovementPacketSnapshot currentSnapshot(BotEntry entry) {
        BotPhysicsEngine.MovementSnapshot snapshot = BotPhysicsEngine.movementSnapshot(entry);
        return new AgentMovementPacketSnapshot(snapshot.velX(), snapshot.velY(), snapshot.stance());
    }
}
