package server.agents.progression;

/** Controls how a quest objective chooses a hunting map. */
enum AgentQuestHuntSelectionMode {
    /** Use only the tested map order carried by the executable plan. */
    FIXED,
    /** Prefer the tested order, but fall back to generated facts when it cannot run. */
    PREFERRED_ADAPTIVE,
    /** Choose from generated facts first, retaining the tested order as a safety fallback. */
    ADAPTIVE
}
