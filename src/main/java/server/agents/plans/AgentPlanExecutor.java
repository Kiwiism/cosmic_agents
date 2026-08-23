package server.agents.plans;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.autonomy.AgentAutonomyKernel;
import server.agents.runtime.autonomy.AgentAutonomySnapshot;
import server.agents.runtime.autonomy.AgentGoalProposal;
import server.agents.runtime.autonomy.AgentGoalSelection;
import server.agents.runtime.autonomy.AgentGoalSelector;
import server.agents.integration.cosmic.CosmicAgentAutonomySnapshotFactory;

import java.util.List;

/** The single lifecycle executor for every versioned Agent plan. */
public final class AgentPlanExecutor implements AgentPlanRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanExecutor.class);

    private final AgentPlanRepository repository;
    private final AgentPlanStepExecutorRegistry stepExecutors;

    public AgentPlanExecutor(AgentPlanRepository repository,
                             AgentPlanStepExecutorRegistry stepExecutors) {
        this.repository = repository;
        this.stepExecutors = stepExecutors;
        for (AgentPlanDefinition plan : repository.all()) {
            for (AgentPlanDefinition.Step step : plan.steps()) {
                stepExecutors.require(step.operation()).validateDefinition(plan, step);
            }
        }
    }

    @Override
    public boolean start(AgentRuntimeEntry entry,
                         Character agent,
                         String planId,
                         AgentPlanStartRequest request,
                         long nowMs) {
        return startWithChain(entry, agent, planId, request, null, nowMs);
    }

    private boolean startWithChain(AgentRuntimeEntry entry,
                                   Character agent,
                                   String planId,
                                   AgentPlanStartRequest request,
                                   String existingChainId,
                                   long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        String selectionCorrelationId =
                (existingChainId == null || existingChainId.isBlank()
                        ? "selection:" + agent.getId() + ':' + nowMs
                        : existingChainId + ":selection")
                        + ':' + planId;
        AgentGoalSelection selection = selectPlan(
                entry, agent, planId, "explicit-plan-request", selectionCorrelationId, nowMs);
        if (!selection.selected()) {
            return false;
        }
        AgentPlanDefinition plan = selection.plan();
        AgentPlanSessionState session = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (!AgentActivityBootstrap.admission().prepare(
                AgentActivityBootstrap.QUESTING_CONTROLLER_ID,
                entry, agent, "replaced by plan " + planId, nowMs)) {
            return false;
        }
        if (session.active()) {
            cancel(entry, agent, "superseded by " + planId, nowMs);
        }
        AgentPlanStartRequest effectiveRequest =
                request == null ? AgentPlanStartRequest.EMPTY : request;
        String chainId = existingChainId == null || existingChainId.isBlank()
                ? chainId(agent, nowMs) : existingChainId;
        AgentPlanSessionHandle inheritedHandle = existingChainId == null
                ? null : session.sessionHandle();
        session.start(plan, chainId, effectiveRequest, nowMs);
        if (inheritedHandle != null) {
            session.own(inheritedHandle);
        } else {
            session.own(new AgentPlanSessionHandle(
                    "plan:" + agent.getId() + ':' + nowMs,
                    "legacy:" + agent.getId() + ':' + nowMs,
                    "legacy-runtime", agent.getId(), plan.planId(), nowMs));
        }
        AgentPlanConditionEvaluator.Evaluation entryCheck =
                AgentPlanConditionEvaluator.evaluateAll(
                        plan.entryCriteria(), entry, agent, session);
        if (!entryCheck.satisfied()) {
            session.terminal(AgentPlanExecutionStatus.BLOCKED,
                    "entry criteria failed: " + entryCheck.reason());
            AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
            return false;
        }
        AgentForegroundPauseRuntime.reset(entry);
        if (plan.objective().registration() == AgentPlanDefinition.Registration.EXECUTOR) {
            startObjective(entry, plan, session.chainId(), nowMs);
        }
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
        return true;
    }

    @Override
    public boolean startAvailableSuccessor(AgentRuntimeEntry entry,
                                           Character agent,
                                           String planId,
                                           AgentPlanStartRequest request,
                                           long nowMs) {
        if (entry == null || agent == null || planId == null || planId.isBlank()) {
            return false;
        }
        AgentPlanSessionState session =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (session.active() || !session.availableSuccessorPlanIds().contains(planId)) {
            return false;
        }
        return startWithChain(entry, agent, planId, request, session.chainId(), nowMs);
    }

    @Override
    public boolean tick(AgentRuntimeEntry entry, Character agent, long wallNowMs) {
        try {
            return tickInternal(entry, agent, wallNowMs);
        } finally {
            AgentPlanCheckpointRuntime.persistIfDirty(entry, wallNowMs);
        }
    }

    private boolean tickInternal(AgentRuntimeEntry entry, Character agent, long wallNowMs) {
        AgentPlanSessionState session = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (!session.active()) {
            return false;
        }
        AgentPlanExitMode pendingExit = session.pendingExitMode();
        if (pendingExit != null) {
            boolean deadlineExpired = session.pendingExitDeadlineMs() > 0L
                    && wallNowMs >= session.pendingExitDeadlineMs();
            if (deadlineExpired && pendingExit == AgentPlanExitMode.SUSPEND_AFTER_STEP) {
                // A suspend deadline bounds how long the owner waits for a natural step boundary;
                // it is not permission to destroy the retained plan. Freeze the exact in-flight
                // step so a later resume can reattach it from the durable checkpoint.
                session.suspendAtBoundary();
                AgentPlanCheckpointRuntime.persistIfDirty(entry, wallNowMs);
                return true;
            }
            if (pendingExit == AgentPlanExitMode.FORCE_NOW || deadlineExpired) {
                cancel(entry, agent, session.pendingExitReason().isBlank()
                        ? "plan ownership deadline expired" : session.pendingExitReason(), wallNowMs);
                return true;
            }
            if (session.atStepBoundary()) {
                if (pendingExit == AgentPlanExitMode.SUSPEND_AFTER_STEP) {
                    session.suspendAtBoundary();
                    AgentPlanCheckpointRuntime.persistIfDirty(entry, wallNowMs);
                    return true;
                }
                cancel(entry, agent, session.pendingExitReason(), wallNowMs);
                return true;
            }
        }
        if (session.suspended()) {
            return true;
        }
        if (AgentForegroundPauseRuntime.paused(entry)) {
            return true;
        }
        long nowMs = AgentForegroundPauseRuntime.effectiveNow(entry, wallNowMs);
        if (!session.pendingSuccessorPlanId().isBlank()) {
            if (nowMs < session.nextActionAtMs()) {
                return true;
            }
            String successor = session.pendingSuccessorPlanId();
            String chainId = session.chainId();
            AgentGoalSelection selection = selectPlan(
                    entry, agent, successor, "automatic-plan-successor",
                    chainId + ":selection:" + successor, nowMs);
            if (!selection.selected()) {
                terminal(entry, session, repository.require(session.planId()),
                        AgentPlanExecutionStatus.BLOCKED,
                        "automatic successor was rejected: " + selection.reason(), nowMs);
                return true;
            }
            AgentPlanDefinition next = selection.plan();
            session.start(next, chainId, AgentPlanStartRequest.EMPTY, nowMs);
            AgentPlanConditionEvaluator.Evaluation entryCheck =
                    AgentPlanConditionEvaluator.evaluateAll(
                            next.entryCriteria(), entry, agent, session);
            if (!entryCheck.satisfied()) {
                terminal(entry, session, next, AgentPlanExecutionStatus.BLOCKED,
                        "successor entry criteria failed: " + entryCheck.reason(), nowMs);
                return true;
            }
            if (next.objective().registration() == AgentPlanDefinition.Registration.EXECUTOR) {
                startObjective(entry, next, chainId, nowMs);
            }
        }

        AgentPlanDefinition plan = repository.require(session.planId());
        if (session.stepIndex() >= plan.steps().size()) {
            completePlan(entry, agent, session, plan, nowMs);
            return true;
        }
        AgentPlanDefinition.Step step = plan.steps().get(session.stepIndex());
        AgentPlanStepExecutor executor = stepExecutors.require(step.operation());
        AgentPlanExecutionContext context = context(entry, agent, session, plan, step, nowMs);
        try {
            if (nowMs < session.nextActionAtMs()) {
                return true;
            }
            if (session.stepStartedValue() && step.timeoutMs() > 0L
                    && nowMs - session.stepStartedAtMs() >= step.timeoutMs()) {
                executor.cancel(context);
                AgentAutonomyKernel.completePlanStep(
                        entry, session, plan, step, AgentPlanExecutionStatus.FAILED,
                        "step timed out after " + step.timeoutMs() + "ms", nowMs);
                return retryOrTerminate(entry, session, plan, step,
                        "step timed out after " + step.timeoutMs() + "ms", nowMs);
            }
            AgentPlanStepExecution result;
            if (!session.stepStartedValue()) {
                session.stepStarted(nowMs);
                AgentAutonomyKernel.beginPlanStep(
                        entry, () -> CosmicAgentAutonomySnapshotFactory.capture(entry, agent, nowMs),
                        session, plan, step, nowMs);
                result = executor.start(context);
                if (result.status() == AgentPlanExecutionStatus.ACTIVE) {
                    markCurrentStepAttached(entry, session);
                }
            } else {
                AgentAutonomyKernel.beginPlanStep(
                        entry, () -> CosmicAgentAutonomySnapshotFactory.capture(entry, agent, nowMs),
                        session, plan, step, nowMs);
                result = executor.tick(context);
            }
            completeDecisionCycleIfTerminal(entry, session, plan, step, result, nowMs);
            if (result.status() == AgentPlanExecutionStatus.SUCCEEDED) {
                session.stepSucceeded();
                if (session.stepIndex() >= plan.steps().size()) {
                    completePlan(entry, agent, session, plan, nowMs);
                }
            } else if (result.status() == AgentPlanExecutionStatus.FAILED) {
                executor.cancel(context);
                return retryOrTerminate(entry, session, plan, step, result.reason(), nowMs);
            } else if (result.status() != AgentPlanExecutionStatus.ACTIVE) {
                terminal(entry, session, plan, result.status(), result.reason(), nowMs);
            }
            // ACTIVE describes the durable plan lifecycle, not ownership of the
            // remainder of this Agent tick. Objective steps deliberately yield
            // with consumed=false after handing work to the shared capability
            // runtime (navigation, NPC interaction, combat, etc.).
            return result.consumed();
        } catch (Exception failure) {
            log.warn("Agent plan step failed agent={} plan={} step={}",
                    agent.getName(), plan.planId(), step.stepId(), failure);
            AgentAutonomyKernel.completePlanStep(
                    entry, session, plan, step, AgentPlanExecutionStatus.FAILED,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(), nowMs);
            try {
                executor.cancel(context);
            } catch (RuntimeException cancelFailure) {
                failure.addSuppressed(cancelFailure);
            }
            return retryOrTerminate(entry, session, plan, step,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(), nowMs);
        }
    }

    @Override
    public boolean cancel(AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentPlanSessionState session = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (!session.active() || session.planId().isBlank()) {
            return false;
        }
        AgentPlanDefinition plan = repository.require(session.planId());
        if (session.stepStartedValue() && session.stepIndex() < plan.steps().size()) {
            AgentPlanDefinition.Step step = plan.steps().get(session.stepIndex());
            AgentAutonomyKernel.beginPlanStep(
                    entry, () -> CosmicAgentAutonomySnapshotFactory.capture(entry, agent, nowMs),
                    session, plan, step, nowMs);
            stepExecutors.require(step.operation()).cancel(
                    context(entry, agent, session, plan, step, nowMs));
            AgentAutonomyKernel.completePlanStep(
                    entry, session, plan, step, AgentPlanExecutionStatus.CANCELLED,
                    reason, nowMs);
        }
        terminal(entry, session, plan, AgentPlanExecutionStatus.CANCELLED, reason, nowMs);
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
        return true;
    }

    @Override
    public boolean reattach(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentPlanSessionState session = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (session.active()) {
            return reattachCurrent(entry, agent, session, nowMs);
        }
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        AgentPlanDefinition plan = planFor(objective);
        if (plan == null) {
            return false;
        }
        session.reattach(plan, nowMs);
        session.own(new AgentPlanSessionHandle(
                "plan:reattach:" + agent.getId() + ':' + nowMs,
                "reattach:" + agent.getId() + ':' + nowMs,
                "legacy-runtime", agent.getId(), plan.planId(), nowMs));
        boolean attached = reattachCurrent(entry, agent, session, nowMs);
        AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
        return attached;
    }

    private boolean reattachCurrent(AgentRuntimeEntry entry,
                                    Character agent,
                                    AgentPlanSessionState session,
                                    long nowMs) {
        AgentPlanDefinition plan = repository.require(session.planId());
        if (session.stepIndex() >= plan.steps().size()) {
            completePlan(entry, agent, session, plan, nowMs);
            return true;
        }
        AgentPlanDefinition.Step step = plan.steps().get(session.stepIndex());
        try {
            if (!session.stepStartedValue()) {
                session.stepStarted(nowMs);
            }
            AgentAutonomyKernel.beginPlanStep(
                    entry, () -> CosmicAgentAutonomySnapshotFactory.capture(entry, agent, nowMs),
                    session, plan, step, nowMs);
            AgentPlanStepExecution result = stepExecutors.require(step.operation()).reattach(
                    context(entry, agent, session, plan, step, nowMs));
            completeDecisionCycleIfTerminal(entry, session, plan, step, result, nowMs);
            if (result.status() == AgentPlanExecutionStatus.ACTIVE) {
                markCurrentStepAttached(entry, session);
            } else if (result.status() == AgentPlanExecutionStatus.SUCCEEDED) {
                session.stepSucceeded();
            } else {
                terminal(entry, session, plan, result.status(), result.reason(), nowMs);
            }
            AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
            return true;
        } catch (Exception failure) {
            AgentAutonomyKernel.completePlanStep(
                    entry, session, plan, step, AgentPlanExecutionStatus.FAILED,
                    "reattach failed: " + failure.getMessage(), nowMs);
            terminal(entry, session, plan, AgentPlanExecutionStatus.FAILED,
                    "reattach failed: " + failure.getMessage(), nowMs);
            AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
            return true;
        }
    }

    private void completePlan(AgentRuntimeEntry entry,
                              Character agent,
                              AgentPlanSessionState session,
                              AgentPlanDefinition plan,
                              long nowMs) {
        AgentPlanConditionEvaluator.Evaluation exitCheck =
                AgentPlanConditionEvaluator.evaluateAll(plan.exitCriteria(), entry, agent, session);
        if (!exitCheck.satisfied()) {
            terminal(entry, session, plan, AgentPlanExecutionStatus.BLOCKED,
                    "exit criteria failed: " + exitCheck.reason(), nowMs);
            return;
        }
        transitionObjective(entry, plan, AgentObjectiveStatus.SUCCEEDED,
                "plan exit criteria satisfied", nowMs);
        session.terminal(AgentPlanExecutionStatus.SUCCEEDED, "plan exit criteria satisfied");
        List<String> available = plan.successors().stream()
                .filter(successor -> successor.on() == AgentPlanDefinition.Outcome.SUCCEEDED
                        && successor.activation() == AgentPlanDefinition.Activation.AVAILABLE)
                .map(AgentPlanDefinition.Successor::planId)
                .toList();
        session.availableSuccessors(available);
        plan.successors().stream()
                .filter(successor -> successor.on() == AgentPlanDefinition.Outcome.SUCCEEDED
                        && successor.activation() == AgentPlanDefinition.Activation.AUTOMATIC)
                .findFirst()
                .ifPresent(successor ->
                        session.waitForSuccessor(successor.planId(), nowMs + successor.delayMs()));
        if (session.pendingSuccessorPlanId().isBlank()) {
            session.captureOutcome(nowMs);
        }
    }

    private boolean retryOrTerminate(AgentRuntimeEntry entry,
                                     AgentPlanSessionState session,
                                     AgentPlanDefinition plan,
                                     AgentPlanDefinition.Step step,
                                     String reason,
                                     long nowMs) {
        if (session.stepAttempt() <= step.retryBudget()) {
            long retryDelayMs = Math.min(30_000L,
                    1_000L << Math.min(5, Math.max(0, session.stepAttempt() - 1)));
            session.retryStep(nowMs + retryDelayMs, reason);
            return true;
        }
        terminal(entry, session, plan, AgentPlanExecutionStatus.FAILED, reason, nowMs);
        return true;
    }

    private void terminal(AgentRuntimeEntry entry,
                          AgentPlanSessionState session,
                          AgentPlanDefinition plan,
                          AgentPlanExecutionStatus status,
                          String reason,
                          long nowMs) {
        AgentObjectiveStatus objectiveStatus = switch (status) {
            case SUCCEEDED -> AgentObjectiveStatus.SUCCEEDED;
            case BLOCKED -> AgentObjectiveStatus.BLOCKED;
            case CANCELLED -> AgentObjectiveStatus.CANCELLED;
            case FAILED -> AgentObjectiveStatus.FAILED;
            case IDLE, ACTIVE -> throw new IllegalArgumentException("terminal status is required");
        };
        transitionObjective(entry, plan, objectiveStatus, reason, nowMs);
        session.terminal(status, reason);
        session.captureOutcome(nowMs);
    }

    private static void startObjective(AgentRuntimeEntry entry,
                                       AgentPlanDefinition plan,
                                       String chainId,
                                       long nowMs) {
        AgentPlanDefinition.ObjectivePolicy policy = plan.objective();
        AgentObjectiveKernel.start(entry, new AgentObjectiveDefinition(
                "plan:" + plan.planId(), policy.type(), policy.priority(), policy.deadlineMs(),
                policy.retryBudget(), policy.source(), policy.behaviorVersion(), chainId), nowMs);
    }

    private static void transitionObjective(AgentRuntimeEntry entry,
                                            AgentPlanDefinition plan,
                                            AgentObjectiveStatus status,
                                            String reason,
                                            long nowMs) {
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active != null && active.type().equals(plan.objective().type())) {
            AgentObjectiveKernel.transition(entry, active.objectiveId(), status, reason, nowMs);
        }
    }

    private AgentPlanDefinition planFor(AgentObjectiveDefinition objective) {
        if (objective == null) {
            return null;
        }
        if (objective.objectiveId().startsWith("plan:")) {
            return repository.find(objective.objectiveId().substring("plan:".length())).orElse(null);
        }
        List<AgentPlanDefinition> matches = repository.all().stream()
                .filter(plan -> plan.objective().type().equals(objective.type())
                        && plan.objective().behaviorVersion().equals(objective.behaviorVersion()))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static AgentPlanExecutionContext context(
            AgentRuntimeEntry entry,
            Character agent,
            AgentPlanSessionState session,
            AgentPlanDefinition plan,
            AgentPlanDefinition.Step step,
            long nowMs) {
        return new AgentPlanExecutionContext(entry, agent, plan, step,
                new AgentPlanStartRequest(session.inputs(), session.transientAttachment()), nowMs);
    }

    private static String chainId(Character agent, long nowMs) {
        return "chain:" + agent.getId() + ':' + nowMs;
    }

    private AgentGoalSelection selectPlan(AgentRuntimeEntry entry,
                                          Character agent,
                                          String planId,
                                          String evidence,
                                          String correlationId,
                                          long nowMs) {
        AgentPlanDefinition requestedPlan = repository.require(planId);
        AgentAutonomySnapshot snapshot =
                CosmicAgentAutonomySnapshotFactory.capture(entry, agent, nowMs);
        AgentGoalSelection selection = AgentGoalSelector.select(
                snapshot,
                List.of(AgentGoalProposal.explicitPlan(
                        requestedPlan.planId(),
                        requestedPlan.objective().type(),
                        requestedPlan.objective().priority(),
                        requestedPlan.objective().source().name(),
                        requestedPlan.objective().behaviorVersion(),
                        nowMs,
                        List.of(evidence))),
                repository,
                nowMs);
        AgentAutonomyKernel.recordGoalSelection(
                entry, snapshot, selection, correlationId, nowMs);
        return selection;
    }

    private static void markCurrentStepAttached(
            AgentRuntimeEntry entry, AgentPlanSessionState session) {
        String attachmentKey = AgentPlanAttachmentKey.current(session);
        if (!attachmentKey.isBlank()) {
            entry.capabilityStates().require(AgentPlanAttachmentState.STATE_KEY)
                    .attached(attachmentKey);
        }
    }

    private static void completeDecisionCycleIfTerminal(
            AgentRuntimeEntry entry,
            AgentPlanSessionState session,
            AgentPlanDefinition plan,
            AgentPlanDefinition.Step step,
            AgentPlanStepExecution result,
            long nowMs) {
        if (result.status() != AgentPlanExecutionStatus.ACTIVE) {
            AgentAutonomyKernel.completePlanStep(
                    entry, session, plan, step, result.status(), result.reason(), nowMs);
        }
    }
}
