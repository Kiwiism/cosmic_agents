package server.agents.capabilities.townlife;

/** Controls how much authority an optional language-model adapter receives. */
public enum AgentTownLifeSupportLevel {
    /** Deterministic TownLife decisions and deterministic presentation. */
    DETERMINISTIC,
    /** Deterministic decisions; an external renderer may propose observer-facing dialogue. */
    DIALOGUE_ONLY,
    /** An external controller may propose validated high-level TownLife directives. */
    DIALOGUE_AND_DECISION
}
