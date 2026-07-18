package server.agents.capabilities.movement;

public enum AgentMovementOutcome {
    WAYPOINT_REACHED,
    CLIMB_ATTACHED,
    CLIMB_DETACHED,
    PORTAL_ENTERED,
    BLOCKED,
    FELL_FROM_REGION,
    ROUTE_INVALIDATED,
    CANCELLED
}
