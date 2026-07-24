package server.agents.capabilities.runtime;

/** Exclusive mutable Agent resources coordinated across capability sessions. */
public enum AgentCapabilityResource {
    MOVEMENT,
    COMBAT,
    INVENTORY,
    NPC_INTERACTION,
    SOCIAL_TRANSACTION
}
