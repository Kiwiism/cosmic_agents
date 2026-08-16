package server.agents.plans;

import server.agents.field.AgentFieldIntent;
import server.agents.field.AgentFieldObservationState;
import server.agents.field.events.AgentFieldLifecycleEvent;
import server.agents.runtime.field.AgentFieldActivityRuntime;
import server.agents.runtime.field.AgentFieldActivityState;
import server.agents.runtime.field.AgentFieldAdmissionMode;
import server.agents.runtime.field.AgentFieldEntryRequest;
import server.agents.runtime.field.AgentFieldExitRequest;
import server.agents.runtime.field.AgentFieldSessionResult;
import server.agents.runtime.field.AgentFieldTerminalState;
import server.agents.runtime.field.AgentFieldVisitLeaseRequest;
import server.agents.runtime.field.AgentFieldVisitLeaseRuntime;
import server.agents.runtime.field.AgentFieldVisitRequest;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reusable bounded field-grinding step; travel and higher-level map choice remain external. */
public final class AgentFieldVisitPlanStepExecutor implements AgentPlanStepExecutor {
    public static final String OPERATION = "field-visit";
    private static final long DEFAULT_GRACEFUL_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.runtime.field.AgentFieldActivityRuntime.DEFAULT_GRACEFUL_EXIT_TIMEOUT_MS");

    @Override
    public String operation() { return OPERATION; }

    @Override
    public void validateDefinition(AgentPlanDefinition plan, AgentPlanDefinition.Step step) {
        Object duration = step.parameters().get("durationMs");
        if (!(duration instanceof Number durationNumber) || durationNumber.longValue() <= 0L) {
            throw invalid(plan, step, "requires positive durationMs");
        }
        Object map = step.parameters().get("fieldMapId");
        if (map != null && (!(map instanceof Number mapNumber) || mapNumber.intValue() <= 0)) {
            throw invalid(plan, step, "fieldMapId must be positive when supplied");
        }
        Object timeout = step.parameters().get("gracefulTimeoutMs");
        if (timeout != null && (!(timeout instanceof Number timeoutNumber)
                || timeoutNumber.longValue() <= 0L)) {
            throw invalid(plan, step, "gracefulTimeoutMs must be positive when supplied");
        }
        Object capacity = step.parameters().get("maximumParticipants");
        if (capacity != null && (!(capacity instanceof Number capacityNumber)
                || capacityNumber.intValue() < 1 || capacityNumber.intValue() > 12)) {
            throw invalid(plan, step, "maximumParticipants must be from 1 to 12");
        }
        Object kills = step.parameters().get("killsPerMob");
        if (kills != null && (!(kills instanceof Number killNumber)
                || killNumber.intValue() <= 0)) {
            throw invalid(plan, step, "killsPerMob must be positive when supplied");
        }
        mobIds(step);
        narrationLevel(step);
    }

    @Override
    public AgentPlanStepExecution start(AgentPlanExecutionContext context) {
        int mapId = intParameter(context.step(), "fieldMapId", context.agent().getMapId());
        if (context.agent().getMapId() != mapId) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "travel must place the Agent in the field map before this step");
        }
        String attachmentKey = AgentPlanAttachmentKey.current(context.entry().capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY));
        String requestId = attachmentKey + ":field";
        String callerId = "universal-plan:" + context.plan().planId() + ':'
                + context.entry().capabilityStates().require(AgentPlanSessionState.STATE_KEY).chainId();
        Set<Integer> targets = mobIds(context.step());
        int killsPerMob = intParameter(context.step(), "killsPerMob", 10);
        Map<Integer, Integer> requirements = new LinkedHashMap<>();
        targets.forEach(mobId -> requirements.put(mobId, Math.max(1, killsPerMob)));
        AgentFieldIntent intent = targets.isEmpty()
                ? AgentFieldIntent.freeGrind(requestId)
                : AgentFieldIntent.partyCoverage(requestId, targets, requirements);
        AgentFieldVisitRequest visit = new AgentFieldVisitRequest(
                mapId, intent, booleanParameter(context.step(), "acceptQuestVisitors", true),
                intParameter(context.step(), "maximumParticipants", 6),
                booleanParameter(context.step(), "restAllowed", true),
                narrationLevel(context.step()));
        AgentFieldVisitLeaseRequest lease = new AgentFieldVisitLeaseRequest(
                new AgentFieldEntryRequest(requestId, callerId, visit),
                AgentFieldAdmissionMode.CREATE_OR_JOIN,
                context.nowMs() + longParameter(context.step(), "durationMs", 0L),
                longParameter(context.step(), "gracefulTimeoutMs", DEFAULT_GRACEFUL_TIMEOUT_MS),
                "planned field duration elapsed");
        AgentFieldSessionResult result = AgentFieldVisitLeaseRuntime.start(
                context.entry(), context.agent(), lease, context.nowMs());
        if (!result.started()
                && result.status() != AgentFieldSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.BLOCKED,
                    result.reason().isBlank() ? result.status().name() : result.reason());
        }
        context.entry().capabilityStates().require(AgentFieldVisitStepState.STATE_KEY)
                .attach(attachmentKey, requestId, result.handle().sessionId());
        return AgentPlanStepExecution.active(false);
    }

    @Override
    public AgentPlanStepExecution tick(AgentPlanExecutionContext context) {
        AgentFieldVisitStepState stepState = context.entry().capabilityStates()
                .require(AgentFieldVisitStepState.STATE_KEY);
        String attachmentKey = AgentPlanAttachmentKey.current(context.entry().capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY));
        if (!stepState.matches(attachmentKey)) return reattach(context);
        if (AgentFieldActivityRuntime.active(context.entry())) {
            return AgentPlanStepExecution.active(false);
        }
        AgentFieldVisitLeaseRuntime.clear(context.entry(), context.agent());
        AgentFieldTerminalState.Snapshot terminal = context.entry().capabilityStates()
                .require(AgentFieldTerminalState.STATE_KEY).snapshot();
        String sessionId = stepState.sessionId();
        stepState.clear();
        if (terminal.matches(sessionId) && terminal.phase() != AgentFieldLifecycleEvent.Phase.EXITED) {
            return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.FAILED,
                    "field visit ended with " + terminal.phase() + ": " + terminal.reason());
        }
        return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                "planned field visit completed");
    }

    @Override
    public AgentPlanStepExecution reattach(AgentPlanExecutionContext context) {
        String attachmentKey = AgentPlanAttachmentKey.current(context.entry().capabilityStates()
                .require(AgentPlanSessionState.STATE_KEY));
        String requestId = attachmentKey + ":field";
        AgentFieldActivityState.Snapshot field = context.entry().capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY).snapshot();
        if (field.active() && requestId.equals(field.handle().requestId())) {
            context.entry().capabilityStates().require(AgentFieldVisitStepState.STATE_KEY)
                    .attach(attachmentKey, requestId, field.handle().sessionId());
            return AgentPlanStepExecution.active(false);
        }
        context.entry().capabilityStates().require(AgentFieldVisitStepState.STATE_KEY).clear();
        return AgentPlanStepExecution.terminal(AgentPlanExecutionStatus.SUCCEEDED,
                "field visit was already terminal at reattachment");
    }

    @Override
    public void cancel(AgentPlanExecutionContext context) {
        AgentFieldActivityState.Snapshot field = context.entry().capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY).snapshot();
        AgentFieldVisitStepState step = context.entry().capabilityStates()
                .require(AgentFieldVisitStepState.STATE_KEY);
        if (field.active() && step.sessionId().equals(field.handle().sessionId())) {
            AgentFieldActivityRuntime.requestExit(context.entry(), context.agent(),
                    AgentFieldExitRequest.force(field.handle(),
                            "owning universal plan was cancelled", context.nowMs()));
        }
        AgentFieldVisitLeaseRuntime.clear(context.entry(), context.agent());
        step.clear();
    }

    private static AgentPlanValidationException invalid(
            AgentPlanDefinition plan, AgentPlanDefinition.Step step, String detail) {
        return new AgentPlanValidationException(plan.planId() + '/' + step.stepId() + ' ' + detail);
    }

    private static Set<Integer> mobIds(AgentPlanDefinition.Step step) {
        Object value = step.parameters().get("mobIds");
        if (value == null) return Set.of();
        if (!(value instanceof List<?> values)) {
            throw new AgentPlanValidationException("field-visit mobIds must be a list");
        }
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Object candidate : values) {
            if (!(candidate instanceof Number number) || number.intValue() <= 0) {
                throw new AgentPlanValidationException("field-visit mobIds must contain positive numbers");
            }
            result.add(number.intValue());
        }
        return Set.copyOf(result);
    }

    private static AgentFieldObservationState.NarrationLevel narrationLevel(
            AgentPlanDefinition.Step step) {
        Object value = step.parameters().get("narrationLevel");
        if (!(value instanceof String text) || text.isBlank()) {
            return AgentFieldObservationState.NarrationLevel.SUMMARY;
        }
        try {
            return AgentFieldObservationState.NarrationLevel.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new AgentPlanValidationException("field-visit narrationLevel must be off, summary, or verbose");
        }
    }

    private static int intParameter(AgentPlanDefinition.Step step, String key, int fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longParameter(AgentPlanDefinition.Step step, String key, long fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static boolean booleanParameter(AgentPlanDefinition.Step step, String key, boolean fallback) {
        Object value = step.parameters().get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }
}
