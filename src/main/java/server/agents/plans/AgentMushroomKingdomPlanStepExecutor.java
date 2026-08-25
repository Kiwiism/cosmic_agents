package server.agents.plans;

import server.agents.progression.AgentMushroomKingdomCatalog;
import server.agents.progression.AgentMushroomKingdomRuntime;
import server.agents.progression.AgentMushroomKingdomState;

/** Universal-plan adapter for the Mushroom Kingdom Questing capability. */
public final class AgentMushroomKingdomPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "mushroom-kingdom-questline";

    @Override public String operation() { return OPERATION; }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        if (context.agent().getLevel() < 30 || context.agent().getLevel() > 38) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Mushroom Kingdom requires level 30 through 38");
        }
        if (!AgentMushroomKingdomCatalog.supportedSecondJob(context.agent().getJob().getId())) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Mushroom Kingdom currently supports Explorer second jobs only");
        }
        context.entry().capabilityStates().require(AgentMushroomKingdomState.STATE_KEY)
                .begin(context.nowMs());
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentMushroomKingdomState state = context.entry().capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        return state.progressAtMs() == 0L ? start(context) : tick(context);
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
