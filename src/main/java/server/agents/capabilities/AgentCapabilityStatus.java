package server.agents.capabilities;

public enum AgentCapabilityStatus {
    SUCCESS,
    NOT_READY,
    MISSING_REQUIREMENT,
    BLOCKED_BY_SCOPE,
    BLOCKED_FORBIDDEN_QUEST,
    BLOCKED_FORBIDDEN_MAP,
    BLOCKED_FORBIDDEN_NPC,
    FAILED
}
