package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.runtime.AgentCapabilityJournalEvent;
import server.agents.capabilities.runtime.AgentCapabilityJournalEventType;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.capabilities.movement.AgentMovementRecoveryService;
import server.agents.capabilities.objective.AgentObjectiveProgressWatchdog;
import server.agents.capabilities.objective.AgentObjectiveRecoveryPolicy;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.async.AgentAsyncCompletion;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public final class AmherstPlanRuntimeRunner {
    private static final Logger log = LoggerFactory.getLogger(AmherstPlanRuntimeRunner.class);
    private final AmherstPlanCard card;
    private final AmherstPlanProgressStore store;
    private final AmherstPlanProgressService progressService;
    private final AmherstObjectiveReconciler reconciler;
    private final AmherstObjectiveHandlerRegistry handlers;
    private final AmherstObjectiveDelay objectiveDelay;
    private final AgentObjectiveRecoveryPolicy recoveryPolicy;
    private final AmherstPlanObjectiveDeferralPolicy deferralPolicy;
    private final Object saveLock = new Object();
    private AmherstPlanProgressSnapshot pendingSave;
    private boolean saveWorkerRunning;
    private boolean saveRetryScheduled;
    private static final long SAVE_RETRY_DELAY_MS = config.AgentTuning.longValue("server.agents.plans.amherst.AmherstPlanRuntimeRunner.SAVE_RETRY_DELAY_MS");

    public AmherstPlanRuntimeRunner(AmherstPlanCard card,
                                    AmherstPlanProgressStore store,
                                    AmherstPlanProgressService progressService,
                                    AmherstObjectiveReconciler reconciler,
                                    AmherstObjectiveHandlerRegistry handlers) {
        this(card, store, progressService, reconciler, handlers, AmherstObjectiveDelay.NONE);
    }

    public AmherstPlanRuntimeRunner(AmherstPlanCard card,
                                    AmherstPlanProgressStore store,
                                    AmherstPlanProgressService progressService,
                                    AmherstObjectiveReconciler reconciler,
                                    AmherstObjectiveHandlerRegistry handlers,
                                    AmherstObjectiveDelay objectiveDelay) {
        this(card, store, progressService, reconciler, handlers, objectiveDelay,
                AgentObjectiveRecoveryPolicy.configured(), AmherstPlanObjectiveDeferralPolicy.NONE);
    }

    public AmherstPlanRuntimeRunner(AmherstPlanCard card,
                                    AmherstPlanProgressStore store,
                                    AmherstPlanProgressService progressService,
                                    AmherstObjectiveReconciler reconciler,
                                    AmherstObjectiveHandlerRegistry handlers,
                                    AmherstObjectiveDelay objectiveDelay,
                                    AmherstPlanObjectiveDeferralPolicy deferralPolicy) {
        this(card, store, progressService, reconciler, handlers, objectiveDelay,
                AgentObjectiveRecoveryPolicy.configured(), deferralPolicy);
    }

    AmherstPlanRuntimeRunner(AmherstPlanCard card,
                             AmherstPlanProgressStore store,
                             AmherstPlanProgressService progressService,
                             AmherstObjectiveReconciler reconciler,
                             AmherstObjectiveHandlerRegistry handlers,
                             AmherstObjectiveDelay objectiveDelay,
                             AgentObjectiveRecoveryPolicy recoveryPolicy) {
        this(card, store, progressService, reconciler, handlers, objectiveDelay,
                recoveryPolicy, AmherstPlanObjectiveDeferralPolicy.NONE);
    }

    AmherstPlanRuntimeRunner(AmherstPlanCard card,
                             AmherstPlanProgressStore store,
                             AmherstPlanProgressService progressService,
                             AmherstObjectiveReconciler reconciler,
                             AmherstObjectiveHandlerRegistry handlers,
                             AmherstObjectiveDelay objectiveDelay,
                             AgentObjectiveRecoveryPolicy recoveryPolicy,
                             AmherstPlanObjectiveDeferralPolicy deferralPolicy) {
        this.card = card;
        this.store = store;
        this.progressService = progressService;
        this.reconciler = reconciler;
        this.handlers = handlers;
        this.objectiveDelay = objectiveDelay == null ? AmherstObjectiveDelay.NONE : objectiveDelay;
        this.recoveryPolicy = recoveryPolicy == null
                ? AgentObjectiveRecoveryPolicy.configured() : recoveryPolicy;
        this.deferralPolicy = deferralPolicy == null
                ? AmherstPlanObjectiveDeferralPolicy.NONE : deferralPolicy;
    }

    public void start(AgentRuntimeEntry entry, Character agent, long nowMs) throws IOException {
        start(entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, AmherstPlanObserver.NONE);
    }

    public void start(AgentRuntimeEntry entry,
                      Character agent,
                      long nowMs,
                      AmherstPlanExecutionMode mode,
                      AmherstPlanObserver observer) throws IOException {
        start(entry, agent, nowMs, mode, observer, 0L);
    }

    public void start(AgentRuntimeEntry entry,
                      Character agent,
                      long nowMs,
                      AmherstPlanExecutionMode mode,
                      AmherstPlanObserver observer,
                      long initialObjectiveDelayMs) throws IOException {
        long firstObjectiveAtMs = nowMs + Math.max(0L, initialObjectiveDelayMs);
        if (asyncPersistenceEnabled()) {
            startAsync(entry, agent, nowMs, mode, observer, firstObjectiveAtMs);
            return;
        }
        initialize(entry, agent, nowMs, mode, observer, firstObjectiveAtMs,
                store.load(card.planId(), agent.getId()));
    }

    private void startAsync(AgentRuntimeEntry entry,
                            Character agent,
                            long nowMs,
                            AmherstPlanExecutionMode mode,
                            AmherstPlanObserver observer,
                            long firstObjectiveAtMs) throws IOException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            state.runner = this;
            state.assignedObjectiveId = null;
            state.active = true;
            state.loading = true;
            state.completed = false;
            state.mode = mode == null ? AmherstPlanExecutionMode.AUTO : mode;
            state.advanceRequested = state.mode == AmherstPlanExecutionMode.MANUAL;
            state.waitingForAdvance = false;
            state.nextObjectiveAtMs = firstObjectiveAtMs;
            state.objectiveWatchdog.reset();
            state.deferredObjectiveIds.clear();
            state.objectiveDeferralStages.clear();
            state.observer = observer == null ? AmherstPlanObserver.NONE : observer;
            state.lastError = "";
        }
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.PERSISTENCE,
                persistenceRequestKey("load"),
                () -> loadProgress(agent.getId()),
                (completionEntry, completion) -> finishAsyncStart(
                        completionEntry, agent, nowMs, mode, observer,
                        firstObjectiveAtMs, completion));
        if (!submission.accepted()) {
            synchronized (state) {
                if (state.runner == this && state.loading) {
                    state.clearRuntime();
                }
            }
            throw new IOException("Agent persistence queue is full");
        }
    }

    private AmherstPlanProgressSnapshot loadProgress(int agentId) {
        try {
            return store.load(card.planId(), agentId);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void finishAsyncStart(AgentRuntimeEntry entry,
                                  Character agent,
                                  long nowMs,
                                  AmherstPlanExecutionMode mode,
                                  AmherstPlanObserver observer,
                                  long firstObjectiveAtMs,
                                  AgentAsyncCompletion<AmherstPlanProgressSnapshot> completion) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            if (state.runner != this || !state.loading) {
                return;
            }
            if (!completion.succeeded()) {
                state.loading = false;
                state.active = false;
                state.lastError = failureMessage(completion);
                publish(state, "PLAN ERROR: " + state.lastError);
                observe(state, new AmherstPlanObservation(
                        AmherstPlanObservation.Type.PLAN_ERROR, nowMs,
                        "", null, null, null, state.lastError));
                return;
            }
        }
        try {
            initialize(entry, agent, nowMs, mode, observer,
                    firstObjectiveAtMs, completion.result());
        } catch (IOException failure) {
            synchronized (state) {
                state.loading = false;
                state.active = false;
                state.lastError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                publish(state, "PLAN ERROR: " + state.lastError);
                observe(state, new AmherstPlanObservation(
                        AmherstPlanObservation.Type.PLAN_ERROR, nowMs,
                        "", null, null, null, state.lastError));
            }
        }
    }

    private void initialize(AgentRuntimeEntry entry,
                            Character agent,
                            long nowMs,
                            AmherstPlanExecutionMode mode,
                            AmherstPlanObserver observer,
                            long firstObjectiveAtMs,
                            AmherstPlanProgressSnapshot loaded) throws IOException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            state.progress = progressService.ensureObjectives(loaded, card, nowMs);
            state.progress = progressService.recoverInterrupted(state.progress, nowMs);
            state.runner = this;
            state.assignedObjectiveId = null;
            state.syncedCapabilityJournalCount = entry.capabilityRuntimeState().journalSnapshot().size();
            state.active = true;
            state.loading = false;
            state.completed = false;
            state.mode = mode == null ? AmherstPlanExecutionMode.AUTO : mode;
            state.advanceRequested = state.mode == AmherstPlanExecutionMode.MANUAL;
            state.waitingForAdvance = false;
            state.nextObjectiveAtMs = firstObjectiveAtMs;
            state.objectiveWatchdog.reset();
            state.deferredObjectiveIds.clear();
            state.objectiveDeferralStages.clear();
            state.observer = observer == null ? AmherstPlanObserver.NONE : observer;
            state.lastError = "";
            saveIfChanged(entry, state, loaded, state.progress);
            publish(state, "Plan loaded in " + state.mode + " mode. " + progressSummary(state.progress));
            observe(state, new AmherstPlanObservation(
                    AmherstPlanObservation.Type.PLAN_STARTED, nowMs,
                    "", null, null, null, state.mode.name()));
        }
    }

    public boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            if (!state.active || state.runner != this) {
                return false;
            }
            if (state.loading) {
                return true;
            }
            try {
                syncCapabilityJournal(entry, state, nowMs);
                boolean finishedObjective = false;
                if (entry.capabilityRuntimeState().hasActiveCapability()) {
                    if (!finishLiveSatisfiedQuest(entry, agent, state, nowMs)) {
                        if (recoverStalledObjective(entry, agent, state, nowMs)) {
                            return true;
                        }
                        return false;
                    }
                    finishedObjective = true;
                }
                if (!finishedObjective && state.assignedObjectiveId != null) {
                    finishAssigned(entry, agent, state, nowMs);
                    finishedObjective = true;
                }
                if (finishedObjective) {
                    if (!state.active) {
                        return true;
                    }
                    if (state.mode == AmherstPlanExecutionMode.AUTO) {
                        state.nextObjectiveAtMs = Math.max(state.nextObjectiveAtMs,
                                nowMs + Math.max(0L, objectiveDelay.nextDelayMs()));
                    }
                }

                if (state.mode == AmherstPlanExecutionMode.MANUAL
                        && !state.advanceRequested && !finishedObjective) {
                    state.waitingForAdvance = true;
                    return false;
                }

                if (nowMs < state.nextObjectiveAtMs) {
                    return true;
                }
                state.nextObjectiveAtMs = 0L;

                AmherstPlanObjective next = reconcileAndFindNext(entry, agent, state, nowMs);
                if (next == null) {
                    AmherstPlanProgressSnapshot before = state.progress;
                    state.progress = progressService.append(state.progress,
                            new AmherstPlanJournalEvent(nowMs, AmherstPlanJournalEventType.PLAN_COMPLETED,
                                    "", "NONE", "all objectives satisfy live state"), nowMs);
                    saveIfChanged(entry, state, before, state.progress);
                    state.active = false;
                    state.completed = true;
                    state.waitingForAdvance = false;
                    publish(state, "PLAN COMPLETE. " + progressSummary(state.progress));
                    observe(state, new AmherstPlanObservation(
                            AmherstPlanObservation.Type.PLAN_COMPLETED, nowMs,
                            "", null, AgentCapabilityStatus.SUCCESS, null,
                            "all objectives satisfy live state"));
                    transitionPlanObjective(entry, AgentObjectiveStatus.SUCCEEDED,
                            "all plan objectives satisfy live state", nowMs);
                    return true;
                }
                if (state.mode == AmherstPlanExecutionMode.MANUAL && !state.advanceRequested) {
                    state.waitingForAdvance = true;
                    publish(state, "Paused. Next: " + AmherstObjectiveFormatter.numbered(card, next));
                    return false;
                }

                AmherstObjectiveExecution execution = handlers.create(card, next);
                if (!AgentCapabilityRuntime.assign(entry, execution.invocation())) {
                    return true;
                }
                AmherstPlanProgressSnapshot before = state.progress;
                state.syncedCapabilityJournalCount = entry.capabilityRuntimeState().journalSnapshot().size();
                state.progress = progressService.start(state.progress, next.objectiveId(), nowMs,
                        state.syncedCapabilityJournalCount);
                state.assignedObjectiveId = next.objectiveId();
                state.objectiveStartLevel = agent.getLevel();
                state.objectiveStartExp = agent.getExp();
                AgentObjectiveProgressWatchdog.start(
                        state.objectiveWatchdog, entry, agent, nowMs);
                state.advanceRequested = false;
                state.waitingForAdvance = false;
                saveIfChanged(entry, state, before, state.progress);
                publish(state, "Starting " + AmherstObjectiveFormatter.numbered(card, next));
                publish(state, "Expected steps: " + AmherstObjectiveFormatter.expectedSteps(next));
                observe(state, new AmherstPlanObservation(
                        AmherstPlanObservation.Type.OBJECTIVE_STARTED, nowMs,
                        next.objectiveId(), next.kind(), null, null, next.reason()));
                AmherstPlanNarrator.announce(agent, next);
                return true;
            } catch (IOException | RuntimeException failure) {
                state.lastError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                state.active = false;
                publish(state, "PLAN ERROR: " + state.lastError);
                observe(state, new AmherstPlanObservation(
                        AmherstPlanObservation.Type.PLAN_ERROR, nowMs,
                        state.assignedObjectiveId, null, null, null, state.lastError));
                return true;
            }
        }
    }

    private boolean finishLiveSatisfiedQuest(AgentRuntimeEntry entry,
                                             Character agent,
                                             AmherstPlanExecutionState state,
                                             long nowMs) throws IOException {
        if (state.assignedObjectiveId == null) {
            return false;
        }
        AmherstPlanObjective objective = objective(state.assignedObjectiveId);
        if (!canRecoverQuestResultFromLiveState(objective.kind())) {
            return false;
        }
        AmherstObjectiveReconciler.Decision decision = reconciler.reconcile(card, objective, agent);
        if (!decision.satisfied()) {
            return false;
        }

        AgentCapabilityRuntime.cancelNow(entry, agent, nowMs);
        finishAssigned(entry, agent, state, nowMs, AgentCapabilityResult.success(
                "authoritative live state satisfied objective; stale capability work cancelled"));
        log.info("Completed active Agent quest objective from live state agent={} map={} objective={}",
                agent.getName(), agent.getMapId(), objective.objectiveId());
        return true;
    }

    public boolean requestAdvance(AgentRuntimeEntry entry) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            if (!state.active || state.completed || state.mode != AmherstPlanExecutionMode.MANUAL
                    || state.loading || state.assignedObjectiveId != null
                    || entry.capabilityRuntimeState().hasActiveCapability()) {
                return false;
            }
            state.advanceRequested = true;
            state.waitingForAdvance = false;
            publish(state, "Advance accepted; assigning the first live-unsatisfied objective.");
            return true;
        }
    }

    public void cancel(AgentRuntimeEntry entry) {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        synchronized (state) {
            if (state.active) {
                AgentCapabilityRuntime.requestCancellation(entry);
                transitionPlanObjective(entry, AgentObjectiveStatus.CANCELLED,
                        "plan cancellation requested", System.currentTimeMillis());
            }
        }
    }

    private void finishAssigned(AgentRuntimeEntry entry,
                                Character agent,
                                AmherstPlanExecutionState state,
                                long nowMs) throws IOException {
        finishAssigned(entry, agent, state, nowMs, entry.capabilityRuntimeState().lastResult());
    }

    private void finishAssigned(AgentRuntimeEntry entry,
                                Character agent,
                                AmherstPlanExecutionState state,
                                long nowMs,
                                AgentCapabilityResult terminalResult) throws IOException {
        AgentCapabilityResult result = terminalResult;
        if (result == null) {
            return;
        }
        AmherstPlanObjective objective = objective(state.assignedObjectiveId);
        if (result.status() == AgentCapabilityStatus.SUCCESS) {
            AmherstObjectiveReconciler.Decision decision = reconciler.reconcile(card, objective, agent);
            if (!decision.satisfied()) {
                result = AgentCapabilityResult.failed(AgentCapabilityReasonCode.LIVE_STATE_MISMATCH,
                        "objective child chain ended but live state is unsatisfied");
            }
        } else if (result.status() != AgentCapabilityStatus.CANCELLED
                && canRecoverQuestResultFromLiveState(objective.kind())) {
            AmherstObjectiveReconciler.Decision decision = reconciler.reconcile(card, objective, agent);
            if (decision.satisfied()) {
                AgentCapabilityStatus recoveredStatus = result.status();
                AgentCapabilityReasonCode recoveredReason = result.reasonCode();
                result = AgentCapabilityResult.success(
                        "live state satisfied after " + recoveredStatus + ": " + decision.reason());
                log.info("Recovered Agent plan objective from live state agent={} map={} objective={} status={} reason={}",
                        agent.getName(), agent.getMapId(), objective.objectiveId(),
                        recoveredStatus, recoveredReason);
            }
        }
        AmherstPlanProgressSnapshot before = state.progress;
        state.progress = progressService.terminal(state.progress, objective.objectiveId(), result,
                nowMs, entry.capabilityRuntimeState().journalSnapshot().size());
        state.assignedObjectiveId = null;
        state.objectiveWatchdog.reset();
        saveIfChanged(entry, state, before, state.progress);
        observe(state, new AmherstPlanObservation(
                AmherstPlanObservation.Type.OBJECTIVE_FINISHED, nowMs,
                objective.objectiveId(), objective.kind(), result.status(), null, result.message()));
        publish(state, result.status() + " " + AmherstObjectiveFormatter.numbered(card, objective)
                + " - " + result.message());
        publish(state, "Agent progress: Lv" + state.objectiveStartLevel + " EXP " + state.objectiveStartExp
                + " -> Lv" + agent.getLevel() + " EXP " + agent.getExp() + ".");
        publish(state, progressSummary(state.progress));
        if (result.status() != AgentCapabilityStatus.SUCCESS) {
            log.warn("Agent plan objective failed agent={} map={} objective={} status={} reason={} message={}",
                    agent.getName(), agent.getMapId(), objective.objectiveId(), result.status(),
                    result.reasonCode(), result.message());
        }
        if (result.status() != AgentCapabilityStatus.SUCCESS) {
            if (queueAutomaticRecovery(entry, agent, state, objective, result, nowMs)) {
                return;
            }
            if (state.mode != AmherstPlanExecutionMode.MANUAL) {
                state.active = false;
                transitionPlanObjective(entry, AgentObjectiveStatus.BLOCKED,
                        result.reasonCode() + ": " + result.message(), nowMs);
            }
        }
    }

    private static void transitionPlanObjective(AgentRuntimeEntry entry,
                                                AgentObjectiveStatus status,
                                                String reason,
                                                long nowMs) {
        AgentObjectiveDefinition active = AgentObjectiveKernel.active(entry);
        if (active != null) {
            AgentObjectiveKernel.transition(entry, active.objectiveId(), status, reason, nowMs);
        }
    }

    private boolean recoverStalledObjective(AgentRuntimeEntry entry,
                                            Character agent,
                                            AmherstPlanExecutionState state,
                                            long nowMs) throws IOException {
        if (state.assignedObjectiveId == null) {
            return false;
        }
        AgentObjectiveProgressWatchdog.Evaluation evaluation =
                AgentObjectiveProgressWatchdog.evaluate(
                        state.objectiveWatchdog, entry, agent, nowMs, recoveryPolicy);
        if (evaluation.action() == AgentObjectiveProgressWatchdog.Action.RECOVER) {
            String objectiveId = state.assignedObjectiveId;
            AgentCapabilityRuntime.cancelNow(entry, agent, nowMs);
            finishAssigned(entry, agent, state, nowMs, new AgentCapabilityResult(
                    AgentCapabilityStatus.TIMED_OUT,
                    AgentCapabilityReasonCode.DEADLINE_EXCEEDED,
                    "objective made no progress for " + evaluation.stalledMs()
                            + " ms; stale capability state cancelled for replanning"));
            log.warn("Recovered stalled Agent objective agent={} map={} objective={} stalledMs={}",
                    agent.getName(), agent.getMapId(), objectiveId, evaluation.stalledMs());
            return true;
        }
        if (evaluation.action() == AgentObjectiveProgressWatchdog.Action.NUDGE) {
            AgentMovementRecoveryService.nudgeForObjectiveReplan(entry);
            publish(state, "No objective progress for " + evaluation.stalledMs()
                    + " ms; cleared stale movement so navigation can replan.");
        }
        return false;
    }

    private boolean queueAutomaticRecovery(AgentRuntimeEntry entry,
                                           Character agent,
                                           AmherstPlanExecutionState state,
                                           AmherstPlanObjective objective,
                                           AgentCapabilityResult result,
                                           long nowMs) throws IOException {
        if (!recoverable(result)) {
            return false;
        }
        AmherstObjectiveProgress durable = state.progress.objectives().get(objective.objectiveId());
        int attempts = durable == null ? 0 : durable.attempts();
        boolean automaticLimitReached = attempts > recoveryPolicy.maxAutomaticRecoveries();
        boolean deferrableWorldResource = canWaitForWorldResource(objective, result)
                && deferralPolicy.canDefer(card, objective, result);
        int nextDeferralStage = state.objectiveDeferralStages
                .getOrDefault(objective.objectiveId(), 0) + 1;
        List<AmherstPlanObjective> alternatives = deferrableWorldResource
                ? deferralPolicy.independentAlternatives(
                        card, objective, state.progress, nextDeferralStage)
                : List.of();
        boolean dependencySafeDeferral = !alternatives.isEmpty();
        boolean declaredEmptyStage = deferrableWorldResource
                && nextDeferralStage <= deferralPolicy.alternativeStageCount(card, objective);
        boolean policyWorldResourceWait = deferrableWorldResource
                && deferralPolicy.waitForWorldResourceAfterAlternatives(
                        card, objective, result, nextDeferralStage);
        boolean worldResourceWait = !dependencySafeDeferral
                && canWaitForWorldResource(objective, result)
                && (automaticLimitReached || policyWorldResourceWait);
        if (automaticLimitReached && !worldResourceWait && !dependencySafeDeferral) {
            publish(state, "Automatic recovery limit reached for " + objective.objectiveId() + ".");
            return false;
        }
        AgentMovementRecoveryService.nudgeForObjectiveReplan(entry);
        if (dependencySafeDeferral || declaredEmptyStage) {
            state.objectiveDeferralStages.put(objective.objectiveId(), nextDeferralStage);
        }
        if (dependencySafeDeferral) {
            state.deferredObjectiveIds.add(objective.objectiveId());
        }
        long retryDelayMs = worldResourceWait
                ? Math.max(recoveryPolicy.recoverAfterMs(), recoveryPolicy.recoveryDelayMs())
                : recoveryPolicy.recoveryDelayMs();
        AmherstPlanProgressSnapshot before = state.progress;
        state.progress = progressService.append(state.progress,
                new AmherstPlanJournalEvent(nowMs, AmherstPlanJournalEventType.RETRY,
                        objective.objectiveId(), result.reasonCode().name(),
                        dependencySafeDeferral
                                ? "scarce world resource deferred for independent plan work after attempt " + attempts
                                : worldResourceWait
                                ? "world-resource recheck queued after attempt " + attempts
                                : "automatic recovery queued after attempt " + attempts), nowMs);
        saveIfChanged(entry, state, before, state.progress);
        state.nextObjectiveAtMs = nowMs + retryDelayMs;
        if (state.mode == AmherstPlanExecutionMode.MANUAL) {
            state.advanceRequested = true;
        }
        publish(state, dependencySafeDeferral
                ? "Temporarily deferred " + AmherstObjectiveFormatter.numbered(card, objective)
                        + "; dependency-safe plan work may continue before this objective is retried."
                : worldResourceWait
                ? "Waiting for world resources for " + AmherstObjectiveFormatter.numbered(card, objective)
                        + "; live state will be checked again in " + retryDelayMs + " ms."
                : "Recovery queued for " + AmherstObjectiveFormatter.numbered(card, objective)
                        + "; the same live-unsatisfied objective will be replanned.");
        log.info("Queued Agent objective recovery agent={} map={} objective={} attempt={} reason={} delayMs={} worldResourceWait={} deferred={}",
                agent.getName(), agent.getMapId(), objective.objectiveId(), attempts,
                result.reasonCode(), retryDelayMs, worldResourceWait, dependencySafeDeferral);
        return true;
    }

    private static boolean canWaitForWorldResource(AmherstPlanObjective objective,
                                                   AgentCapabilityResult result) {
        return objective.kind().waitsForWorldResource()
                && (result.status() == AgentCapabilityStatus.TIMED_OUT
                || result.reasonCode() == AgentCapabilityReasonCode.RETRIES_EXHAUSTED);
    }

    private static boolean recoverable(AgentCapabilityResult result) {
        return result.status() == AgentCapabilityStatus.TIMED_OUT
                || result.reasonCode() == AgentCapabilityReasonCode.RETRIES_EXHAUSTED
                || result.reasonCode() == AgentCapabilityReasonCode.EXECUTION_FAILED
                || result.reasonCode() == AgentCapabilityReasonCode.LIVE_STATE_MISMATCH;
    }

    private static boolean canRecoverQuestResultFromLiveState(AmherstPlanObjectiveKind kind) {
        return switch (kind) {
            case QUEST_START, QUEST_COMPLETE, FORCE_COMPLETE_QUEST,
                    QUEST_CHAIN, QUEST_CHAIN_IF_AVAILABLE -> true;
            default -> false;
        };
    }

    private AmherstPlanObjective reconcileAndFindNext(AgentRuntimeEntry entry,
                                                      Character agent,
                                                      AmherstPlanExecutionState state,
                                                      long nowMs) throws IOException {
        AmherstPlanProgressSnapshot before = state.progress;
        AmherstPlanObjective firstUnsatisfied = null;
        for (AmherstPlanObjective objective : card.objectives()) {
            AmherstObjectiveProgress durable = state.progress.objectives().get(objective.objectiveId());
            if (objective.kind() == AmherstPlanObjectiveKind.STOP_PLAN
                    && (durable == null || durable.status() != AmherstObjectiveProgressStatus.SATISFIED)) {
                firstUnsatisfied = objective;
                break;
            }
            AmherstObjectiveReconciler.Decision decision = reconciler.reconcile(card, objective, agent);
            state.progress = progressService.reconcile(state.progress, objective.objectiveId(),
                    decision.satisfied(), decision.reason(), nowMs);
            if (decision.satisfied()) {
                state.deferredObjectiveIds.remove(objective.objectiveId());
                state.objectiveDeferralStages.remove(objective.objectiveId());
            }
            if (!decision.satisfied() && firstUnsatisfied == null) {
                firstUnsatisfied = objective;
                break;
            }
        }
        if (firstUnsatisfied != null
                && state.deferredObjectiveIds.contains(firstUnsatisfied.objectiveId())) {
            AmherstPlanObjective alternative = reconcileIndependentAlternative(
                    agent, state, firstUnsatisfied, nowMs);
            if (alternative != null) {
                firstUnsatisfied = alternative;
            } else {
                state.deferredObjectiveIds.remove(firstUnsatisfied.objectiveId());
            }
        }
        saveIfChanged(entry, state, before, state.progress);
        return firstUnsatisfied;
    }

    private AmherstPlanObjective reconcileIndependentAlternative(
            Character agent,
            AmherstPlanExecutionState state,
            AmherstPlanObjective blocked,
            long nowMs) {
        for (AmherstPlanObjective alternative : deferralPolicy.independentAlternatives(
                card, blocked, state.progress,
                state.objectiveDeferralStages.getOrDefault(blocked.objectiveId(), 1))) {
            AmherstObjectiveReconciler.Decision decision = reconciler.reconcile(
                    card, alternative, agent);
            state.progress = progressService.reconcile(state.progress, alternative.objectiveId(),
                    decision.satisfied(), decision.reason(), nowMs);
            if (!decision.satisfied()) {
                return alternative;
            }
        }
        return null;
    }

    private void syncCapabilityJournal(AgentRuntimeEntry entry,
                                       AmherstPlanExecutionState state,
                                       long nowMs) throws IOException {
        if (state.assignedObjectiveId == null) {
            return;
        }
        List<AgentCapabilityJournalEvent> journal = entry.capabilityRuntimeState().journalSnapshot();
        int from = Math.min(state.syncedCapabilityJournalCount, journal.size());
        AmherstPlanProgressSnapshot before = state.progress;
        for (int i = from; i < journal.size(); i++) {
            AgentCapabilityJournalEvent event = journal.get(i);
            AmherstPlanJournalEventType type = translate(event.type());
            if (type != null) {
                state.progress = progressService.append(state.progress,
                        new AmherstPlanJournalEvent(event.timestampMs(), type, state.assignedObjectiveId,
                                event.reasonCode().name(), event.capabilityId() + ": " + event.message()), nowMs);
            }
            publishCapabilityEvent(state, event);
            observe(state, new AmherstPlanObservation(
                    AmherstPlanObservation.Type.CAPABILITY_EVENT, event.timestampMs(),
                    state.assignedObjectiveId, null, event.status(), event, event.message()));
        }
        state.syncedCapabilityJournalCount = journal.size();
        saveIfChanged(entry, state, before, state.progress);
    }

    private static AmherstPlanJournalEventType translate(AgentCapabilityJournalEventType type) {
        return switch (type) {
            case HANDOFF_REQUESTED, CHILD_STARTED -> AmherstPlanJournalEventType.CHILD_HANDOFF;
            case CHILD_RESULT, PARENT_RESUMED -> AmherstPlanJournalEventType.CHILD_RESULT;
            case RETRY -> AmherstPlanJournalEventType.RETRY;
            default -> null;
        };
    }

    private AmherstPlanObjective objective(String objectiveId) {
        return card.objectives().stream()
                .filter(objective -> objective.objectiveId().equals(objectiveId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("assigned objective is not in plan"));
    }

    private void saveIfChanged(AgentRuntimeEntry entry,
                               AmherstPlanExecutionState state,
                               AmherstPlanProgressSnapshot before,
                               AmherstPlanProgressSnapshot after) throws IOException {
        if (before == after) {
            return;
        }
        if (!asyncPersistenceEnabled()) {
            store.save(after);
            return;
        }
        enqueueLatestSave(entry, state, after);
    }

    private void enqueueLatestSave(AgentRuntimeEntry entry,
                                   AmherstPlanExecutionState state,
                                   AmherstPlanProgressSnapshot snapshot) {
        synchronized (saveLock) {
            pendingSave = snapshot;
            if (saveWorkerRunning) {
                return;
            }
            saveWorkerRunning = true;
        }
        submitSaveWorker(entry, state);
    }

    private void submitSaveWorker(AgentRuntimeEntry entry, AmherstPlanExecutionState state) {
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.PERSISTENCE,
                persistenceRequestKey("save"),
                this::drainLatestSaves,
                (completionEntry, completion) -> finishSaveWorker(
                        completionEntry, state, completion));
        if (!submission.accepted()) {
            synchronized (saveLock) {
                saveWorkerRunning = false;
            }
            scheduleSaveRetry(entry, state);
        }
    }

    private Void drainLatestSaves() {
        while (true) {
            AmherstPlanProgressSnapshot snapshot;
            synchronized (saveLock) {
                snapshot = pendingSave;
                pendingSave = null;
            }
            if (snapshot == null) {
                return null;
            }
            try {
                store.save(snapshot);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private void finishSaveWorker(AgentRuntimeEntry entry,
                                  AmherstPlanExecutionState state,
                                  AgentAsyncCompletion<Void> completion) {
        boolean restart;
        synchronized (saveLock) {
            saveWorkerRunning = false;
            restart = pendingSave != null;
        }
        if (!completion.succeeded()) {
            synchronized (state) {
                if (state.runner == this) {
                    state.lastError = failureMessage(completion);
                    state.active = false;
                    publish(state, "PLAN ERROR: " + state.lastError);
                }
            }
            return;
        }
        if (restart) {
            synchronized (saveLock) {
                if (saveWorkerRunning || pendingSave == null) {
                    return;
                }
                saveWorkerRunning = true;
            }
            submitSaveWorker(entry, state);
        }
    }

    private void scheduleSaveRetry(AgentRuntimeEntry entry, AmherstPlanExecutionState state) {
        synchronized (saveLock) {
            if (saveRetryScheduled || pendingSave == null) {
                return;
            }
            saveRetryScheduled = true;
        }
        try {
            AgentSchedulerRuntime.schedule(entry, () -> {
                synchronized (saveLock) {
                    saveRetryScheduled = false;
                    if (saveWorkerRunning || pendingSave == null) {
                        return;
                    }
                    saveWorkerRunning = true;
                }
                submitSaveWorker(entry, state);
            }, SAVE_RETRY_DELAY_MS);
        } catch (RuntimeException failure) {
            synchronized (saveLock) {
                saveRetryScheduled = false;
            }
            synchronized (state) {
                if (state.runner == this) {
                    state.lastError = "Unable to retry progress persistence: " + failure.getMessage();
                    state.active = false;
                    publish(state, "PLAN ERROR: " + state.lastError);
                }
            }
        }
    }

    private boolean asyncPersistenceEnabled() {
        return AgentSchedulerConfig.fromSystemProperties().mode() != AgentSchedulerMode.LEGACY_PER_AGENT;
    }

    private String persistenceRequestKey(String operation) {
        return "amherst:" + card.planId() + ":" + operation;
    }

    private static String failureMessage(AgentAsyncCompletion<?> completion) {
        Throwable failure = completion.failure();
        if (failure instanceof UncheckedIOException unchecked && unchecked.getCause() != null) {
            failure = unchecked.getCause();
        }
        return failure == null
                ? completion.status().name()
                : failure.getClass().getSimpleName() + ": " + failure.getMessage();
    }

    private void publishCapabilityEvent(AmherstPlanExecutionState state,
                                        AgentCapabilityJournalEvent event) {
        switch (event.type()) {
            case CHILD_STARTED -> publish(state, "-> Started child: " + event.capabilityId());
            case CHILD_RESULT -> publish(state, "-> Child " + event.capabilityId() + " returned "
                    + event.status() + ": " + event.message());
            case RETRY -> publish(state, "-> Retry " + event.capabilityId() + ": " + event.message());
            case BLOCKED -> publish(state, "-> Blocked " + event.capabilityId() + ": " + event.message());
            default -> {
            }
        }
    }

    private String progressSummary(AmherstPlanProgressSnapshot snapshot) {
        long satisfied = snapshot.objectives().values().stream()
                .filter(progress -> progress.status() == AmherstObjectiveProgressStatus.SATISFIED)
                .count();
        return "Overall progress: " + satisfied + "/" + card.objectives().size() + " objectives satisfied.";
    }

    private static void publish(AmherstPlanExecutionState state, String message) {
        try {
            state.observer.publish(message);
        } catch (RuntimeException ignored) {
            // Observability must never stop plan execution.
        }
    }

    private static void observe(AmherstPlanExecutionState state, AmherstPlanObservation observation) {
        try {
            state.observer.observe(observation);
        } catch (RuntimeException ignored) {
            // Observability must never stop plan execution.
        }
    }
}
