package server.agents.plans;

import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeLifecycleEvent;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.runtime.townlife.AgentTownLifeTerminalState;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRequest;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;

/** Reusable universal-plan step for a bounded, externally owned TownLife visit. */
public final class AgentTownLifeVisitPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "town-life-visit";
    private static final long DEFAULT_GRACEFUL_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.plans.AgentTownLifeVisitPlanStepExecutor.DEFAULT_GRACEFUL_TIMEOUT_MS");

    @Override
    public String operation() {
        return OPERATION;
    }

    @Override
    public void validateDefinition(AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        Object duration = step.parameters().get("durationMs");
        if (!(duration instanceof Number durationNumber) || durationNumber.longValue() <= 0L) {
            throw new AgentPlanValidationException(plan.planId() + '/' + step.stepId()
                    + " requires positive durationMs");
        }
        Object map = step.parameters().get("townMapId");
        if (map != null && (!(map instanceof Number mapNumber) || mapNumber.intValue() <= 0)) {
            throw new AgentPlanValidationException(plan.planId() + '/' + step.stepId()
                    + " townMapId must be positive when supplied");
        }
        Object graceful = step.parameters().get("gracefulTimeoutMs");
        if (graceful != null
                && (!(graceful instanceof Number gracefulNumber)
                || gracefulNumber.longValue() <= 0L)) {
            throw new AgentPlanValidationException(plan.planId() + '/' + step.stepId()
                    + " gracefulTimeoutMs must be positive when supplied");
        }
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        int townMapId = intParameter(context.step(), "townMapId", context.agent().getMapId());
        if (context.agent().getMapId() != townMapId) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "travel must place the Agent in the TownLife map before this step");
        }
        long durationMs = longParameter(context.step(), "durationMs", 0L);
        long gracefulTimeoutMs = longParameter(
                context.step(), "gracefulTimeoutMs", DEFAULT_GRACEFUL_TIMEOUT_MS);
        String attachmentKey = AgentPlanAttachmentKey.current(
                context.entry().capabilityStates().require(AgentPlanSessionState.STATE_KEY));
        String requestId = attachmentKey + ":townlife";
        String callerId = "universal-plan:" + context.plan().planId() + ':'
                + context.entry().capabilityStates().require(AgentPlanSessionState.STATE_KEY).chainId();
        String reason = stringParameter(context.step(), "reason", "planned TownLife visit");
        AgentTownLifeVisitRequest visit = new AgentTownLifeVisitRequest(
                townMapId, AgentTownLifeVisitRequest.Purpose.SYSTEM, reason, 0L);
        AgentTownLifeVisitLeaseRequest lease = new AgentTownLifeVisitLeaseRequest(
                AgentTownLifeEntryRequest.external(requestId, callerId, visit),
                AgentTownLifeAdmissionMode.MANUAL_ONLY,
                context.nowMs() + durationMs, gracefulTimeoutMs,
                "planned TownLife duration elapsed");
        AgentTownLifeSessionResult result = AgentTownLifeVisitLeaseRuntime.start(
                context.entry(), context.agent(), lease, context.nowMs(), context.agent().getId());
        if (!result.started()
                && result.status() != AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    result.reason().isBlank() ? result.status().name() : result.reason());
        }
        context.entry().capabilityStates().require(AgentTownLifeVisitStepState.STATE_KEY)
                .attach(attachmentKey, requestId, result.handle().sessionId());
        return AgentPlanStepExecution.active(false);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        AgentTownLifeVisitStepState state = context.entry().capabilityStates()
                .require(AgentTownLifeVisitStepState.STATE_KEY);
        String attachmentKey = AgentPlanAttachmentKey.current(
                context.entry().capabilityStates().require(AgentPlanSessionState.STATE_KEY));
        if (!state.matches(attachmentKey)) {
            return reattach(context);
        }
        if (AgentTownLifeRuntime.active(context.entry())) {
            return AgentPlanStepExecution.active(false);
        }
        AgentTownLifeVisitLeaseRuntime.clear(context.entry(), context.agent());
        AgentTownLifeTerminalState.Snapshot terminal = context.entry().capabilityStates()
                .require(AgentTownLifeTerminalState.STATE_KEY).snapshot();
        String sessionId = state.sessionId();
        state.clear();
        if (terminal.matches(sessionId)
                && terminal.phase() != AgentTownLifeLifecycleEvent.Phase.EXITED) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.FAILED,
                    "TownLife visit ended with " + terminal.phase() + ": " + terminal.reason());
        }
        return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                "planned TownLife visit completed");
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        AgentPlanSessionState session = context.entry().capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY);
        String attachmentKey = AgentPlanAttachmentKey.current(session);
        String requestId = attachmentKey + ":townlife";
        AgentTownLifeState townState = context.entry().capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        if (townState != null && townState.enabled() && requestId.equals(townState.requestId())) {
            context.entry().capabilityStates().require(AgentTownLifeVisitStepState.STATE_KEY)
                    .attach(attachmentKey, requestId, townState.sessionId());
            return AgentPlanStepExecution.active(false);
        }
        // TownLife and its lease checkpoints are restored before universal-plan reattachment.
        // If neither exists, the bounded visit reached a terminal boundary before restart and
        // the step advances rather than starting a duplicate visit.
        context.entry().capabilityStates().require(AgentTownLifeVisitStepState.STATE_KEY).clear();
        return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                "TownLife visit was already terminal at reattachment");
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentTownLifeState townState = context.entry().capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        AgentTownLifeVisitStepState stepState = context.entry().capabilityStates()
                .require(AgentTownLifeVisitStepState.STATE_KEY);
        if (townState != null && townState.enabled()
                && stepState.sessionId().equals(townState.sessionId())) {
            var handle = townState.sessionHandle(context.agent().getId());
            if (handle != null) {
                AgentTownLifeRuntime.requestExit(context.entry(), context.agent(),
                        AgentTownLifeExitRequest.force(handle,
                                "owning universal plan was cancelled", context.nowMs()));
            }
        }
        AgentTownLifeVisitLeaseRuntime.clear(context.entry(), context.agent());
        stepState.clear();
    }

    private static int intParameter(AgentPlanDefinition.Step step, String key, int fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longParameter(AgentPlanDefinition.Step step, String key, long fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static String stringParameter(
            AgentPlanDefinition.Step step, String key, String fallback) {
        Object value = step.parameters().get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : fallback;
    }
}
