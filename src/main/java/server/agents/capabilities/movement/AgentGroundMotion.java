package server.agents.capabilities.movement;

/**
 * Result of one Agent ground-motion integration step.
 */
public record AgentGroundMotion(int stepX, boolean lostGround) {
}
