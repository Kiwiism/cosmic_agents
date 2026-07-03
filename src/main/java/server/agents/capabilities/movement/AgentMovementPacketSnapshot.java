package server.agents.capabilities.movement;

/**
 * Read-only packet-facing view of current Agent movement state.
 */
public record AgentMovementPacketSnapshot(int velX, int velY, int stance) {
}
