package server.agents.capabilities.objective;

import server.agents.capabilities.navigation.AgentPortalRoutePolicy;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.Map;
import java.util.Set;

/** Generic terminal objective: travel, verify live state, then delegate plan-specific presentation. */
public final class PlanStopObjectiveCapability
        implements AgentExecutableCapability<PlanStopObjectiveCapability.Command> {
    public record Command(String objectiveId, int finalMapId,
                          Map<Integer, Integer> expectedQuestStatuses,
                          Set<Integer> forbiddenCompletedQuestIds,
                          String reason) implements AgentCapabilityCommand {
        public Command(String objectiveId, int finalMapId,
                       Set<Integer> forbiddenCompletedQuestIds, String reason) {
            this(objectiveId, finalMapId, Map.of(), forbiddenCompletedQuestIds, reason);
        }
        public Command {
            expectedQuestStatuses = expectedQuestStatuses == null ? Map.of() : Map.copyOf(expectedQuestStatuses);
            forbiddenCompletedQuestIds = forbiddenCompletedQuestIds == null
                    ? Set.of() : Set.copyOf(forbiddenCompletedQuestIds);
            if (objectiveId == null || objectiveId.isBlank() || finalMapId <= 0
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("plan-stop parameters are required");
            }
        }
        @Override public String type() { return "plan-stop-objective"; }
    }

    private final AmherstObjectiveCapabilitySupport support;
    private final AgentPlanCompletionBehavior completionBehavior;

    public PlanStopObjectiveCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy(),
                AgentPlanCompletionBehavior.COMPLETE, AgentPortalRoutePolicy.DIRECT);
    }
    public PlanStopObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        this(gateway, new AmherstScopePolicy(), AgentPlanCompletionBehavior.COMPLETE,
                AgentPortalRoutePolicy.DIRECT);
    }
    public PlanStopObjectiveCapability(PrimitiveCapabilityGateway gateway, AmherstScopePolicy scopePolicy) {
        this(gateway, scopePolicy, AgentPlanCompletionBehavior.COMPLETE, AgentPortalRoutePolicy.DIRECT);
    }
    public PlanStopObjectiveCapability(PrimitiveCapabilityGateway gateway, AmherstScopePolicy scopePolicy,
                                       AgentPlanCompletionBehavior completionBehavior,
                                       AgentPortalRoutePolicy routePolicy) {
        support = new AmherstObjectiveCapabilitySupport(
                gateway, scopePolicy, AmherstNpcInteractionDelay.NONE, routePolicy);
        this.completionBehavior = completionBehavior == null
                ? AgentPlanCompletionBehavior.COMPLETE : completionBehavior;
    }

    @Override public String id() { return "plan-stop-objective"; }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            completionBehavior.abort(context);
            return failure;
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            AgentCapabilityStep travel = support.travel(context, command.finalMapId());
            if (travel != null) return travel;
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.finalState(command.finalMapId(),
                            command.expectedQuestStatuses(), Map.of(), command.forbiddenCompletedQuestIds()),
                    "plan stop requests final live-state verification");
        }
        return completionBehavior.tick(context, command.objectiveId(), command.reason());
    }
}
