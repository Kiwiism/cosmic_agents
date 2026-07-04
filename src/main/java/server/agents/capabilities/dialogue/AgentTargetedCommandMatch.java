package server.agents.capabilities.dialogue;

import server.agents.runtime.AgentRuntimeHandle;

public record AgentTargetedCommandMatch<E extends AgentRuntimeHandle>(
        E entry,
        String commandText,
        String feedbackMessage) {
}
