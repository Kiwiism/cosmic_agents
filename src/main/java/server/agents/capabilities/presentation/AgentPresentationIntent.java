package server.agents.capabilities.presentation;

/** Cosmetic intent. Execution remains inside existing movement/combat capabilities. */
public enum AgentPresentationIntent {
    WAIT,
    TURN,
    PRONE,
    PRONE_TAP,
    SHUFFLE,
    HOP,
    COMBAT_PAUSE,
    COMBAT_REPOSITION,
    LINGER
}
