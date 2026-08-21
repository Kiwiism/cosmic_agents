package server.agents.progression.questwork;

/** Advisory next operation emitted by live-state reconciliation. */
public enum AgentQuestWorkAction {
    WAIT,
    TRAVEL_TO_START,
    ACCEPT_QUEST,
    TRAVEL_TO_HUNT_MAP,
    COMPLETE_OBJECTIVES,
    RETURN_TO_TURN_IN,
    TURN_IN_QUEST,
    COMPLETE,
    MANUAL_REVIEW
}
