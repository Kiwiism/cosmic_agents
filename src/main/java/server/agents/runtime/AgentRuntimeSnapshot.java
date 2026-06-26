package server.agents.runtime;

import server.agents.model.AgentId;

public record AgentRuntimeSnapshot(AgentId id, String mode) {
}

