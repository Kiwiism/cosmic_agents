package server.agents.runtime.decision;

/** Deployment stage for one versioned behavior implementation. */
public enum AgentBehaviorRouteMode {
    LEGACY,
    SHADOW,
    CANARY,
    ACTIVE
}
