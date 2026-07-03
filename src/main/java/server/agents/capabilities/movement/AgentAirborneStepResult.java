package server.agents.capabilities.movement;

/**
 * Agent-owned result of one airborne movement integration step.
 */
public enum AgentAirborneStepResult {
    WALL,
    CEILING,
    LANDED,
    CONTINUE
}
