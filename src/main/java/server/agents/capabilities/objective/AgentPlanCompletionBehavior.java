package server.agents.capabilities.objective;

import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;

/** Optional plan-owned behavior that runs after generic terminal-state verification. */
@FunctionalInterface
public interface AgentPlanCompletionBehavior {
    AgentPlanCompletionBehavior COMPLETE = (context, objectiveId, reason) ->
            AgentCapabilityStep.terminal(AgentCapabilityResult.success(
                    reason, new AgentObjectiveResult(objectiveId, reason)));

    AgentCapabilityStep tick(AgentCapabilityContext context, String objectiveId, String reason);

    default void abort(AgentCapabilityContext context) {
    }
}
