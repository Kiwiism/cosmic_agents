package server.agents.capabilities.inventory.demand;

/** Mutually exclusive demand buckets, ordered from authoritative to advisory. */
public enum AgentQuestDemandCategory {
    ACTIVE,
    COMMITTED,
    WITHIN_5_LEVELS,
    WITHIN_15_LEVELS,
    WITHIN_25_LEVELS
}
