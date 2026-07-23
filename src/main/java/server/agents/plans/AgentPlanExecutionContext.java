package server.agents.plans;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public record AgentPlanExecutionContext(
        AgentRuntimeEntry entry,
        Character agent,
        AgentPlanDefinition plan,
        AgentPlanDefinition.Step step,
        AgentPlanStartRequest request,
        long nowMs) {
}
