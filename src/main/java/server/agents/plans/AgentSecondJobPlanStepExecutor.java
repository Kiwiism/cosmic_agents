package server.agents.plans;

import server.agents.progression.AgentCareerProgressionState;
import server.agents.progression.AgentSecondJobAdvancementRuntime;
import server.agents.progression.AgentSecondJobAdvancementState;
import server.agents.progression.AgentSecondJobCatalog;

public final class AgentSecondJobPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "second-job-advancement";

    @Override
    public String operation() { return OPERATION; }

    @Override
    public void validateDefinition(AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        Object branch = step.parameters().get("defaultBranch");
        if (branch != null) {
            try {
                AgentSecondJobCatalog.require(String.valueOf(branch));
            } catch (IllegalArgumentException invalid) {
                throw new AgentPlanValidationException(plan.planId() + '/' + step.stepId()
                        + " has invalid defaultBranch " + branch);
            }
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        String requested = textInput(context, "branch");
        int targetJobId = context.request().intInput("targetJobId", 0);
        AgentSecondJobCatalog.Branch branch;
        try {
            if (targetJobId > 0) {
                branch = AgentSecondJobCatalog.forTargetJob(targetJobId);
            } else if (!requested.isBlank()) {
                branch = AgentSecondJobCatalog.require(requested);
            } else {
                Object configured = context.step().parameters().get("defaultBranch");
                if (configured != null) {
                    branch = AgentSecondJobCatalog.require(String.valueOf(configured));
                } else {
                    AgentCareerProgressionState career = context.entry().capabilityStates()
                            .require(AgentCareerProgressionState.STATE_KEY);
                    String bundleId = career.bundle() == null ? "" : career.bundle().bundleId();
                    branch = AgentSecondJobCatalog.require(AgentSecondJobCatalog.defaultBranch(
                            bundleId, context.agent().getJob().getId()));
                }
            }
        } catch (IllegalArgumentException failure) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    failure.getMessage());
        }
        String requestedFamily = textInput(context, "family");
        if (!requestedFamily.isBlank()
                && !branch.family().name().equalsIgnoreCase(requestedFamily.replace('-', '_'))) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "requested family " + requestedFamily + " does not own branch " + branch.id());
        }
        AgentSecondJobAdvancementState state = context.entry().capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        try {
            state.begin(branch.id(), context.nowMs());
        } catch (IllegalStateException conflict) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    conflict.getMessage());
        }
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentSecondJobAdvancementState state = context.entry().capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        return state.branchId().isBlank() ? start(context) : tick(context);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentSecondJobAdvancementRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentSecondJobAdvancementState state = context.entry().capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        return switch (state.phase()) {
            case COMPLETE -> AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    state.reason());
            case BLOCKED -> AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    state.reason());
            default -> AgentPlanStepExecution.active(consumed);
        };
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentSecondJobAdvancementRuntime.cancel(context.entry(), context.agent());
    }

    private static String textInput(AgentPlanExecutionContext context, String key) {
        Object value = context.request().inputs().get(key);
        return value instanceof String text ? text.trim() : "";
    }
}
