package server.agents.plans;

import server.agents.progression.AgentVictoriaTrainingObjectiveRuntime;
import server.agents.progression.AgentVictoriaTrainingState;
import server.agents.objectives.AgentObjectiveState;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.objectives.AgentObjectiveAttachment;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;

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
        int requestedQuestId = context.request().intInput("questId", 0);
        if (!AgentVictoriaTrainingObjectiveRuntime.start(
                context.entry(), context.agent(), targetLevel, questsEnabled,
                requestedQuestId, context.nowMs())) {
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
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(context.entry());
        if (objective == null
                || !AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE.equals(objective.type())) {
            return start(context);
        }
        AgentObjectiveAttachment attachment = AgentVictoriaTrainingObjectiveRuntime.reattach(
                context.entry(), context.agent(), objective, context.nowMs());
        AgentVictoriaTrainingState state = context.entry().capabilityStates()
                .require(AgentVictoriaTrainingState.STATE_KEY);
        if ((attachment == AgentObjectiveAttachment.ATTACHED
                || attachment == AgentObjectiveAttachment.ALREADY_ATTACHED)
                && state.active()) {
            return AgentPlanStepExecution.active(true);
        }
        return tick(context);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentVictoriaTrainingObjectiveRuntime.tick(
                context.entry(), context.agent(), context.nowMs());
        AgentVictoriaTrainingState state = context.entry().capabilityStates()
                .require(AgentVictoriaTrainingState.STATE_KEY);
        if (!state.active()) {
            var terminal = context.entry().capabilityStates()
                    .require(AgentObjectiveState.STATE_KEY).journalSnapshot().reversed().stream()
                    .filter(event -> event.objectiveId().startsWith("victoria:training:"))
                    .findFirst().orElse(null);
            if (terminal != null && terminal.status() == AgentObjectiveStatus.BLOCKED) {
                return AgentPlanStepExecution.terminal(
                        AgentPlanExecutionStatus.BLOCKED, terminal.reason());
            }
            if (terminal != null && terminal.status() == AgentObjectiveStatus.FAILED) {
                return AgentPlanStepExecution.terminal(
                        AgentPlanExecutionStatus.FAILED, terminal.reason());
            }
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
