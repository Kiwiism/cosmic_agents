package server.agents.capabilities.movement;

/**
 * Continuous ground-physics carry state for Agent walking integration.
 */
public record AgentGroundTravelState(double physX, double hspeed, double carryMs) {
}
