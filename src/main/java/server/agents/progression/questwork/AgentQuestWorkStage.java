package server.agents.progression.questwork;

/** Reconciled cursor; child capabilities execute the indicated operation. */
public enum AgentQuestWorkStage {
    TRAVEL_TO_START,
    ACCEPT_QUEST,
    COMPLETE_OBJECTIVES,
    RETURN_TO_TURN_IN,
    TURN_IN_QUEST,
    COMPLETE
}
