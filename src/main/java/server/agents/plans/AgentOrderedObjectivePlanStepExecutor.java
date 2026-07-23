package server.agents.plans;

import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.amherst.AmherstPlanExecutionMode;
import server.agents.plans.amherst.AmherstPlanExecutionState;
import server.agents.plans.amherst.AmherstPlanObserver;
import server.agents.plans.mapleisland.AgentMapleIslandPlanRuntime;

public final class AgentOrderedObjectivePlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "ordered-objective-card";

    @Override
    public String operation() {
        return OPERATION;
    }

    @Override
    public void validateDefinition(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        String variant = String.valueOf(step.parameters().getOrDefault("variant", ""));
        if (!java.util.Set.of("amherst", "southperry", "full").contains(variant)) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId() + " requires a supported variant");
        }
        Object cardPath = step.parameters().get("cardPath");
        if (!(cardPath instanceof String path) || path.isBlank()) {
            throw new AgentPlanValidationException(
                    plan.planId() + '/' + step.stepId() + " requires cardPath");
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) throws Exception {
        String variant = String.valueOf(context.step().parameters().getOrDefault("variant", ""));
        AgentOrderedPlanStartOptions options =
                context.request().transientAttachment() instanceof AgentOrderedPlanStartOptions value
                        ? value : AgentOrderedPlanStartOptions.automatic(AmherstPlanObserver.NONE);
        if ("amherst".equals(variant)) {
            if (options.mode() == AmherstPlanExecutionMode.MANUAL) {
                AgentAmherstPlanRuntime.startManual(context.entry(), context.agent(), context.nowMs(),
                        options.observer());
            } else {
                AgentAmherstPlanRuntime.startAuto(context.entry(), context.agent(), context.nowMs(),
                        options.observer());
            }
        } else if ("southperry".equals(variant)) {
            if (options.mode() == AmherstPlanExecutionMode.MANUAL) {
                AgentMapleIslandPlanRuntime.startManual(context.entry(), context.agent(), context.nowMs(),
                        options.observer());
            } else {
                AgentMapleIslandPlanRuntime.startAuto(context.entry(), context.agent(), context.nowMs(),
                        options.observer());
            }
        } else if ("full".equals(variant)) {
            if (options.mode() == AmherstPlanExecutionMode.MANUAL) {
                AgentMapleIslandPlanRuntime.startFullManual(context.entry(), context.agent(), context.nowMs(),
                        options.observer());
            } else {
                AgentMapleIslandPlanRuntime.startFullAuto(context.entry(), context.agent(), context.nowMs(),
                        options.observer(), options.initialObjectiveDelayMs());
            }
        } else {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "unsupported ordered objective variant " + variant);
        }
        return AgentPlanStepExecution.active(true);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        boolean consumed = AgentAmherstPlanRuntime.tickGate(
                context.entry(), context.agent(), context.nowMs());
        AmherstPlanExecutionState state = context.entry().amherstPlanExecutionState();
        if (state.completed()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                    "ordered objectives completed");
        }
        if (!state.active() && !state.lastError().isBlank()) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.FAILED, state.lastError());
        }
        return AgentPlanStepExecution.active(consumed);
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) throws Exception {
        return start(new AgentPlanExecutionContext(context.entry(), context.agent(), context.plan(),
                context.step(), AgentPlanStartRequest.EMPTY, context.nowMs()));
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentAmherstPlanRuntime.cancel(context.entry());
    }
}
