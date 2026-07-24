package server.agents.plans;

import server.agents.progression.AgentVictoriaTrainingObjectiveRuntime;
import server.agents.progression.AgentVictoriaTrainingState;

public final class AgentVictoriaTrainingPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "victoria-training";

    @Override
    public String operation() {
        return OPERATION;
    }

    @Override
    public void validateDefinition(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        Object target = step.parameters().get("targetLevel");
        if (target != null && (!(target instanceof Number level)
                || level.intValue() < 16 || level.intValue() > 30)) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId()
                            + " targetLevel must be between 16 and 30");
        }
        Object questsEnabled = step.parameters().get("questsEnabled");
        if (questsEnabled != null && !(questsEnabled instanceof Boolean)) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId()
                            + " questsEnabled must be boolean");
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        int targetLevel = intParameter(context, "targetLevel",
                context.request().intInput("targetLevel", -1));
        boolean questsEnabled = booleanParameter(context, "questsEnabled",
                context.request().booleanInput("questsEnabled", true));
        if (!AgentVictoriaTrainingObjectiveRuntime.start(
                context.entry(), context.agent(), targetLevel, questsEnabled, context.nowMs())) {
            if (context.agent().getLevel() >= targetLevel && targetLevel > 0) {
                return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                        "target level already reached");
            }
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "Victoria training could not start for target level " + targetLevel);
        }
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentVictoriaTrainingObjectiveRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentVictoriaTrainingState state = context.entry().capabilityStates()
                .require(AgentVictoriaTrainingState.STATE_KEY);
        if (!state.active()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    "Victoria training target reached");
        }
        return AgentPlanStepExecution.active(consumed);
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentVictoriaTrainingObjectiveRuntime.cancel(context.entry(), context.nowMs());
    }

    private static int intParameter(AgentPlanExecutionContext context, String key, int fallback) {
        Object value = context.step().parameters().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean booleanParameter(
            AgentPlanExecutionContext context, String key, boolean fallback) {
        Object value = context.step().parameters().get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
