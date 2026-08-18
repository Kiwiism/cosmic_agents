package server.agents.capabilities.combat;

/** Explainable reason for the target tier selected before normal combat scoring. */
public enum AgentCombatDecisionReason {
    CLOSEST_ELIGIBLE,
    REQUIRED_LOCAL,
    PLATFORM_BATCH_CLEAR,
    INCIDENTAL_PLATFORM_SWEEP,
    REQUIRED_DEBT,
    INCIDENTAL_NO_REQUIRED_AVAILABLE,
    CLOSEST_REACHABLE_FALLBACK,
    ROUTE_BLOCKER,
    EVADE_BLOCKER
}
