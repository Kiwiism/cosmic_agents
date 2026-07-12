package server.agents.capabilities.runtime;

public enum AgentCapabilityJournalEventType {
    STARTED,
    HANDOFF_REQUESTED,
    CHILD_STARTED,
    CHILD_RESULT,
    PARENT_RESUMED,
    RETRY,
    BLOCKED,
    CANCELLED,
    SUCCEEDED,
    FAILED,
    TIMED_OUT
}
