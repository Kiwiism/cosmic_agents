package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityOutput;

public record AgentObjectiveResult(String objectiveId, String outcome) implements AgentCapabilityOutput {
}
