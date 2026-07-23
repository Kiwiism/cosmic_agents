package server.agents.plans;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentForegroundPauseRuntime;

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
        AgentPlanDefinition plan = repository.require(planId);
        AgentPlanSessionState session = entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        if (session.active()) {
            cancel(entry, agent, "superseded by " + planId, nowMs);
        }
        AgentPlanStartRequest effectiveRequest =
                request == null ? AgentPlanStartRequest.EMPTY : request;
        String chainId = existingChainId == null || existingChainId.isBlank()
                ? chainId(agent, nowMs) : existingChainId;
        session.start(plan, chainId, effectiveRequest, nowMs);
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
            AgentPlanDefinition next = repository.require(successor);
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
                return retryOrTerminate(entry, session, plan, step,
                        "step timed out after " + step.timeoutMs() + "ms", nowMs);
            }
            AgentPlanStepExecution result;
            if (!session.stepStartedValue()) {
                session.stepStarted(nowMs);
                result = executor.start(context);
            } else {
                result = executor.tick(context);
            }
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
            return result.consumed() || session.active();
        } catch (Exception failure) {
            log.warn("Agent plan step failed agent={} plan={} step={}",
                    agent.getName(), plan.planId(), step.stepId(), failure);
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
        if (session.stepIndex() < plan.steps().size()) {
            AgentPlanDefinition.Step step = plan.steps().get(session.stepIndex());
            stepExecutors.require(step.operation()).cancel(
                    context(entry, agent, session, plan, step, nowMs));
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
            AgentPlanStepExecution result = stepExecutors.require(step.operation()).reattach(
                    context(entry, agent, session, plan, step, nowMs));
            if (result.status() == AgentPlanExecutionStatus.ACTIVE) {
                // The capability-specific runner is attached to the restored universal cursor.
            } else if (result.status() == AgentPlanExecutionStatus.SUCCEEDED) {
                session.stepSucceeded();
            } else {
                terminal(entry, session, plan, result.status(), result.reason(), nowMs);
            }
            AgentPlanCheckpointRuntime.persistIfDirty(entry, nowMs);
            return true;
        } catch (Exception failure) {
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
}
