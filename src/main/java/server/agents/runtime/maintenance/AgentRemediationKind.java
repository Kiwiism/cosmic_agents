package server.agents.runtime.maintenance;

/** Typed reason why foreground intent was interrupted. */
public enum AgentRemediationKind {
    DEATH,
    LOW_SUPPLIES,
    FULL_INVENTORY,
    MISSING_EQUIPMENT,
    INSUFFICIENT_MESOS,
    BLOCKED_NAVIGATION
}
