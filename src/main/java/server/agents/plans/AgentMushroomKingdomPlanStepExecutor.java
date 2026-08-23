package server.agents.plans;

import server.agents.progression.AgentMushroomKingdomRuntime;
import server.agents.progression.AgentMushroomKingdomState;

/** Universal-plan adapter for the Mushroom Kingdom Questing capability. */
public final class AgentMushroomKingdomPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "mushroom-kingdom-questline";

    @Override public String operation() { return OPERATION; }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        context.entry().capabilityStates().require(AgentMushroomKingdomState.STATE_KEY)
                .begin(context.nowMs());
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        return tick(context);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentMushroomKingdomRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentMushroomKingdomState state = context.entry().capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        return switch (state.phase()) {
            case COMPLETE -> AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.SUCCEEDED, state.reason());
            case BLOCKED -> AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.BLOCKED, state.reason());
            case ACTIVE -> AgentPlanStepExecution.active(consumed);
        };
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentMushroomKingdomRuntime.cancel(context.entry(), context.agent());
    }
}
