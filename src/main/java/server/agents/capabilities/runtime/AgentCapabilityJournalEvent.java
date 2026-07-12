package server.agents.capabilities.runtime;

import server.agents.capabilities.AgentCapabilityStatus;

public record AgentCapabilityJournalEvent(
        long timestampMs,
        AgentCapabilityJournalEventType type,
        String capabilityId,
        String commandType,
        AgentCapabilityStatus status,
        AgentCapabilityReasonCode reasonCode,
        String message) {
}
