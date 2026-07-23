package server.agents.capabilities.navigation;

/**
 * Capability-neutral result of advancing an Agent toward another map.
 *
 * <p>The caller owns the reason for travelling; the navigation adapter owns
 * the concrete route catalog, portal selection, and edge-health policy.</p>
 */
public enum AgentRouteStatus {
    ARRIVED,
    MOVING,
    NO_ROUTE,
    PORTAL_UNAVAILABLE
}
