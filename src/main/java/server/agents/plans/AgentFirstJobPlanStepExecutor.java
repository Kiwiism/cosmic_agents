package server.agents.plans;

import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.progression.AgentFirstJobJourneyRuntime;
import server.agents.runtime.AgentModeStateRuntime;

import java.util.List;

public final class AgentFirstJobPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "staged-first-job-journey";

    @Override
    public String operation() {
        return OPERATION;
    }

    @Override
    public void validateDefinition(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        Object bundles = step.parameters().get("bundleIds");
        Object stageContract = step.parameters().get("stageContractResource");
        boolean validBundles = bundles instanceof List<?> values
                && !values.isEmpty()
                && values.stream().allMatch(value -> value instanceof String text && !text.isBlank());
        boolean validContract = stageContract instanceof String text && !text.isBlank();
        if (!validBundles && !validContract) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId()
                            + " requires bundleIds or stageContractResource");
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        AgentCareerProgressionState career = context.entry().capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        if (career.bundle() == null) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "a durable career bundle is required");
        }
        Object configured = context.step().parameters().get("bundleIds");
        if (configured instanceof List<?> bundleIds
                && bundleIds.stream().noneMatch(value ->
                career.bundle().bundleId().equals(String.valueOf(value)))) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "career bundle " + career.bundle().bundleId()
                            + " is not eligible for " + context.plan().planId());
        }
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentFirstJobJourneyRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentCareerProgressionState career = context.entry().capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        if (career.stage() == AgentCareerProgressionState.Stage.COMPLETE) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    "first-job level-15 journey completed");
        }
        if (career.stage() == AgentCareerProgressionState.Stage.BLOCKED) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    career.blockReason());
        }
        if (AgentMoveTargetStateRuntime.hasMoveTarget(context.entry())
                || AgentModeStateRuntime.grinding(context.entry())
                || AgentShopStateRuntime.shopVisitPending(context.entry())) {
            consumed = false;
        }
        return AgentPlanStepExecution.active(consumed);
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(context.entry());
        if (active != null) {
            AgentObjectiveKernel.transition(context.entry(), active.objectiveId(),
                    AgentObjectiveStatus.CANCELLED, "universal plan cancelled", context.nowMs());
        }
    }
}
