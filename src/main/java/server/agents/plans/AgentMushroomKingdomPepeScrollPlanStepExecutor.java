package server.agents.plans;

import client.QuestStatus;
import server.agents.progression.AgentMushroomKingdomPepeScrollRuntime;
import server.agents.progression.AgentMushroomKingdomPostStoryState;
import server.agents.progression.AgentPepeEquipmentCatalog;

/** Universal-plan adapter for one bounded repeat of King Pepe's Scroll. */
public final class AgentMushroomKingdomPepeScrollPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "mushroom-kingdom-pepe-scroll";

    @Override public String operation() { return OPERATION; }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        if (context.agent().getLevel() < 30 || context.agent().getLevel() > 38
                || context.agent().getQuestStatus(2336)
                != QuestStatus.Status.COMPLETED.getId()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "King Pepe's Scroll requires completed Mushroom Kingdom at level 30 through 38");
        }
        if (!AgentPepeEquipmentCatalog.capture(context.agent()).scrollable()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "the exact planned Pepe weapon is missing or has no upgrade slots");
        }
        context.entry().capabilityStates().require(
                AgentMushroomKingdomPostStoryState.STATE_KEY).begin(
                AgentMushroomKingdomPostStoryState.Activity.PEPE_SCROLL, context.nowMs());
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentMushroomKingdomPostStoryState state = context.entry().capabilityStates()
                .require(AgentMushroomKingdomPostStoryState.STATE_KEY);
        return state.activity() == AgentMushroomKingdomPostStoryState.Activity.PEPE_SCROLL
                ? tick(context) : start(context);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentMushroomKingdomPepeScrollRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentMushroomKingdomPostStoryState state = context.entry().capabilityStates()
                .require(AgentMushroomKingdomPostStoryState.STATE_KEY);
        return switch (state.phase()) {
            case ACTIVE -> AgentPlanStepExecution.active(consumed);
            case COMPLETE -> AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.SUCCEEDED, state.reason());
            case BLOCKED -> AgentPlanStepExecution.terminal(
                    AgentPlanExecutionStatus.BLOCKED, state.reason());
        };
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentMushroomKingdomPepeScrollRuntime.cancel(context.entry());
    }
}
