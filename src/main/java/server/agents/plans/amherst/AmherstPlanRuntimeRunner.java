package server.agents.plans.amherst;

import client.Character;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.runtime.AgentCapabilityJournalEvent;
import server.agents.capabilities.runtime.AgentCapabilityJournalEventType;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.async.AgentAsyncCompletion;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public final class AmherstPlanRuntimeRunner {
    private final AmherstPlanCard card;
    private final AmherstPlanProgressStore store;
    private final AmherstPlanProgressService progressService;
    private final AmherstObjectiveReconciler reconciler;
    private final AmherstObjectiveHandlerRegistry handlers;
    private final AmherstObjectiveDelay objectiveDelay;

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
        this.card = card;
        this.store = store;
        this.progressService = progressService;
        this.reconciler = reconciler;
        this.handlers = handlers;
        this.objectiveDelay = objectiveDelay == null ? AmherstObjectiveDelay.NONE : objectiveDelay;
    }

    public void start(AgentRuntimeEntry entry, Character agent, long nowMs) throws IOException {
        start(entry, agent, nowMs, AmherstPlanExecutionMode.AUTO, AmherstPlanObserver.NONE);
    }

    public void start(AgentRuntimeEntry entry,
                      Character agent,
                      long nowMs,
                      AmherstPlanExecutionMode mode,
                      AmherstPlanObserver observer) throws IOException {
        if (asyncPersistenceEnabled()) {
            startAsync(entry, agent, nowMs, mode, observer);
            return;
        }
        initialize(entry, agent, nowMs, mode, observer,
                store.load(card.planId(), agent.getId()));
    }

    private void startAsync(AgentRuntimeEntry entry,
                            Character agent,
                            long nowMs,
                            AmherstPlanExecutionMode mode,
                            AmherstPlanObserver observer) throws IOException {
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
            state.nextObjectiveAtMs = 0L;
            state.observer = observer == null ? AmherstPlanObserver.NONE : observer;
            state.lastError = "";
        }
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.PERSISTENCE,
                persistenceRequestKey("load"),
                () -> loadProgress(agent.getId()),
                (completionEntry, completion) -> finishAsyncStart(
                        completionEntry, agent, nowMs, mode, observer, completion));
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
                return;
            }
        }
        try {
            initialize(entry, agent, nowMs, mode, observer, completion.result());
        } catch (IOException failure) {
            synchronized (state) {
                state.loading = false;
                state.active = false;
                state.lastError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                publish(state, "PLAN ERROR: " + state.lastError);
            }
        }
    }

    private void initialize(AgentRuntimeEntry entry,
                            Character agent,
                            long nowMs,
                            AmherstPlanExecutionMode mode,
                            AmherstPlanObserver observer,
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
            state.nextObjectiveAtMs = 0L;
            state.observer = observer == null ? AmherstPlanObserver.NONE : observer;
            state.lastError = "";
            saveIfChanged(entry, state, loaded, state.progress);
            publish(state, "Plan loaded in " + state.mode + " mode. " + progressSummary(state.progress));
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
                if (entry.capabilityRuntimeState().hasActiveCapability()) {
                    return false;
                }
                boolean finishedObjective = false;
                if (state.assignedObjectiveId != null) {
                    finishAssigned(entry, agent, state, nowMs);
                    finishedObjective = true;
                    if (!state.active) {
                        return true;
                    }
                    if (state.mode == AmherstPlanExecutionMode.AUTO) {
                        state.nextObjectiveAtMs = nowMs + Math.max(0L, objectiveDelay.nextDelayMs());
                    }
                }

                if (state.mode == AmherstPlanExecutionMode.MANUAL
                        && !state.advanceRequested && !finishedObjective) {
                    state.waitingForAdvance = true;
                    return false;
                }

                if (state.mode == AmherstPlanExecutionMode.AUTO && nowMs < state.nextObjectiveAtMs) {
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
                state.advanceRequested = false;
                state.waitingForAdvance = false;
                saveIfChanged(entry, state, before, state.progress);
                publish(state, "Starting " + AmherstObjectiveFormatter.numbered(card, next));
                publish(state, "Expected steps: " + AmherstObjectiveFormatter.expectedSteps(next));
                AmherstPlanNarrator.announce(agent, next);
                return true;
            } catch (IOException | RuntimeException failure) {
                state.lastError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                state.active = false;
                publish(state, "PLAN ERROR: " + state.lastError);
                return true;
            }
        }
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
            }
        }
    }

    private void finishAssigned(AgentRuntimeEntry entry,
                                Character agent,
                                AmherstPlanExecutionState state,
                                long nowMs) throws IOException {
        AgentCapabilityResult result = entry.capabilityRuntimeState().lastResult();
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
        }
        AmherstPlanProgressSnapshot before = state.progress;
        state.progress = progressService.terminal(state.progress, objective.objectiveId(), result,
                nowMs, entry.capabilityRuntimeState().journalSnapshot().size());
        state.assignedObjectiveId = null;
        saveIfChanged(entry, state, before, state.progress);
        publish(state, result.status() + " " + AmherstObjectiveFormatter.numbered(card, objective)
                + " - " + result.message());
        publish(state, "Agent progress: Lv" + state.objectiveStartLevel + " EXP " + state.objectiveStartExp
                + " -> Lv" + agent.getLevel() + " EXP " + agent.getExp() + ".");
        publish(state, progressSummary(state.progress));
        if (result.status() != AgentCapabilityStatus.SUCCESS
                && state.mode != AmherstPlanExecutionMode.MANUAL) {
            state.active = false;
        }
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
            if (!decision.satisfied() && firstUnsatisfied == null) {
                firstUnsatisfied = objective;
                break;
            }
        }
        saveIfChanged(entry, state, before, state.progress);
        return firstUnsatisfied;
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
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.PERSISTENCE,
                persistenceRequestKey("save"),
                () -> {
                    try {
                        store.save(after);
                        return null;
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                },
                (completionEntry, completion) -> {
                    if (completion.succeeded()) {
                        return;
                    }
                    synchronized (state) {
                        if (state.runner == this) {
                            state.lastError = failureMessage(completion);
                            state.active = false;
                            publish(state, "PLAN ERROR: " + state.lastError);
                        }
                    }
                });
        if (!submission.accepted()) {
            throw new IOException("Agent persistence queue is full");
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
}
