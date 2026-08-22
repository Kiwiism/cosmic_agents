package server.agents.runtime.journey;

/** Stable event families used to reconstruct one Agent's autonomous journey. */
public enum AgentJourneyEventType {
    DECISION_RECOMMENDED,
    RECOVERY_RECOMMENDED,
    QUEST_WORK_RECONCILED,
    QUEST_SHADOW_COMPARED,
    DIRECTOR_MODE_CHANGED,
    DIRECTIVE_SUBMITTED,
    DIRECTIVE_RESOLVED,
    HANDOFF_PHASE_CHANGED,
    ACTIVITY_TERMINAL
}
