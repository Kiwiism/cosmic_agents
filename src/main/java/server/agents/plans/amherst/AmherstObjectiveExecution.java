package server.agents.plans.amherst;

import server.agents.capabilities.runtime.AgentCapabilityInvocation;

public record AmherstObjectiveExecution(String objectiveId, AgentCapabilityInvocation<?> invocation) {
}
