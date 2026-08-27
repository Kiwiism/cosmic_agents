package server.agents.plans;

import client.QuestStatus;
import server.agents.progression.AgentMushroomKingdomFarmProgress;
import server.agents.progression.AgentMushroomKingdomFarmProgressRuntime;
import server.agents.progression.AgentMushroomKingdomPostStoryState;
import server.agents.progression.AgentMushroomKingdomYetiFarmRuntime;
import server.agents.progression.AgentPepeEquipmentCatalog;

/** Universal-plan adapter for one bounded post-story Yeti campaign. */
public final class AgentMushroomKingdomYetiFarmPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "mushroom-kingdom-yeti-farm";

    @Override public String operation() { return OPERATION; }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        if (context.agent().getLevel() < 30 || context.agent().getLevel() > 38
                || context.agent().getQuestStatus(2336)
                != QuestStatus.Status.COMPLETED.getId()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Yeti farming requires completed Mushroom Kingdom at level 30 through 38");
        }
        if (AgentPepeEquipmentCatalog.capture(context.agent()).owned()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    "desired Pepe weapon is already owned");
        }
        AgentMushroomKingdomFarmProgress progress =
                AgentMushroomKingdomFarmProgressRuntime.beginYetiCampaign(
                        context.agent().getId(), context.nowMs());
        if (progress.yetiCooldownUntilMs() > context.nowMs()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "the ten-run Yeti campaign is on cooldown");
        }
        context.entry().capabilityStates().require(
                AgentMushroomKingdomPostStoryState.STATE_KEY).begin(
                AgentMushroomKingdomPostStoryState.Activity.YETI_FARM, context.nowMs());
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentMushroomKingdomPostStoryState state = context.entry().capabilityStates()
                .require(AgentMushroomKingdomPostStoryState.STATE_KEY);
        return state.activity() == AgentMushroomKingdomPostStoryState.Activity.YETI_FARM
                ? tick(context) : start(context);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentMushroomKingdomYetiFarmRuntime.tick(
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
        AgentMushroomKingdomYetiFarmRuntime.cancel(context.entry(), context.agent());
    }
}
