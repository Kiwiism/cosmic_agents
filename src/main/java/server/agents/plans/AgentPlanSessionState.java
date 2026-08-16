package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;
import java.util.Map;

/** Session ownership for the single universal foreground plan executor. */
public final class AgentPlanSessionState {
    public static final AgentCapabilityStateKey<AgentPlanSessionState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.universal-plan", AgentPlanSessionState.class,
                    AgentPlanSessionState::new);

    private String planId = "";
    private String planVersion = "";
    private String chainId = "";
    private int stepIndex;
    private boolean stepStarted;
    private int stepAttempt;
    private long stepStartedAtMs;
    private AgentPlanExecutionStatus status = AgentPlanExecutionStatus.IDLE;
    private Map<String, Object> inputs = Map.of();
    private Object transientAttachment;
    private String pendingSuccessorPlanId = "";
    private List<String> availableSuccessorPlanIds = List.of();
    private String deferredSuccessorPlanId = "";
    private long nextActionAtMs;
    private String reason = "";
    private AgentPlanSessionHandle sessionHandle;
    private boolean suspended;
    private AgentPlanExitMode pendingExitMode;
    private String pendingExitReason = "";
    private long pendingExitDeadlineMs;
    private AgentPlanOutcome lastOutcome;
    private long revision;
    private long persistedRevision;

    public synchronized void start(AgentPlanDefinition plan,
                                   String chainId,
                                   AgentPlanStartRequest request,
                                   long nowMs) {
        this.planId = plan.planId();
        this.planVersion = plan.planVersion();
        this.chainId = chainId == null || chainId.isBlank() ? plan.planId() : chainId;
        this.stepIndex = 0;
        this.stepStarted = false;
        this.stepAttempt = 0;
        this.stepStartedAtMs = 0L;
        this.status = AgentPlanExecutionStatus.ACTIVE;
        this.inputs = request == null ? Map.of() : request.inputs();
        this.transientAttachment = request == null ? null : request.transientAttachment();
        this.pendingSuccessorPlanId = "";
        this.availableSuccessorPlanIds = List.of();
        this.nextActionAtMs = Math.max(0L, nowMs);
        this.reason = "";
        this.sessionHandle = null;
        this.suspended = false;
        this.pendingExitMode = null;
        this.pendingExitReason = "";
        this.pendingExitDeadlineMs = 0L;
        this.lastOutcome = null;
        changed();
    }

    public synchronized void own(AgentPlanSessionHandle handle) {
        sessionHandle = handle;
        changed();
    }

    public synchronized void requestExit(AgentPlanExitRequest request) {
        pendingExitMode = request.mode();
        pendingExitReason = request.reason();
        pendingExitDeadlineMs = request.deadlineMs();
        changed();
    }

    public synchronized void suspendAtBoundary() {
        suspended = true;
        pendingExitMode = null;
        pendingExitReason = "";
        pendingExitDeadlineMs = 0L;
        changed();
    }

    public synchronized void resume() {
        if (!suspended) return;
        suspended = false;
        pendingExitMode = null;
        pendingExitReason = "";
        pendingExitDeadlineMs = 0L;
        changed();
    }

    public synchronized void reattach(AgentPlanDefinition plan, long nowMs) {
        start(plan, plan.planId(), AgentPlanStartRequest.EMPTY, nowMs);
    }

    public synchronized void stepStarted(long nowMs) {
        stepStarted = true;
        stepAttempt++;
        stepStartedAtMs = Math.max(0L, nowMs);
        changed();
    }

    public synchronized void stepSucceeded() {
        stepIndex++;
        stepStarted = false;
        stepAttempt = 0;
        stepStartedAtMs = 0L;
        transientAttachment = null;
        changed();
    }

    public synchronized void retryStep(long nextAttemptAtMs, String reason) {
        stepStarted = false;
        stepStartedAtMs = 0L;
        nextActionAtMs = Math.max(0L, nextAttemptAtMs);
        this.reason = reason == null ? "" : reason;
        changed();
    }

    public synchronized void terminal(AgentPlanExecutionStatus status, String reason) {
        this.status = status;
        this.reason = reason == null ? "" : reason;
        this.stepStarted = false;
        this.stepStartedAtMs = 0L;
        this.transientAttachment = null;
        this.suspended = false;
        this.pendingExitMode = null;
        this.pendingExitReason = "";
        this.pendingExitDeadlineMs = 0L;
        changed();
    }

    public synchronized void captureOutcome(long nowMs) {
        AgentPlanSessionPhase phase = phase();
        if (!phase.terminal()) return;
        lastOutcome = new AgentPlanOutcome(
                phase, sessionHandle, reason,
                phase == AgentPlanSessionPhase.BLOCKED || phase == AgentPlanSessionPhase.FAILED,
                stepIndex, inputs, availableSuccessorPlanIds, Math.max(0L, nowMs));
        changed();
    }

    public synchronized void waitForSuccessor(String planId, long atMs) {
        pendingSuccessorPlanId = planId == null ? "" : planId;
        nextActionAtMs = Math.max(0L, atMs);
        changed();
    }

    public synchronized void availableSuccessors(List<String> planIds) {
        availableSuccessorPlanIds = planIds == null ? List.of() : List.copyOf(planIds);
        changed();
    }

    public synchronized void deferSuccessor(String planId) {
        String normalized = planId == null ? "" : planId.trim();
        if (!deferredSuccessorPlanId.equals(normalized)) {
            deferredSuccessorPlanId = normalized;
            changed();
        }
    }

    public synchronized void clearDeferredSuccessor(String planId) {
        if (planId != null && deferredSuccessorPlanId.equals(planId.trim())) {
            deferredSuccessorPlanId = "";
            changed();
        }
    }

    public synchronized void clear() {
        planId = "";
        planVersion = "";
        chainId = "";
        stepIndex = 0;
        stepStarted = false;
        stepAttempt = 0;
        stepStartedAtMs = 0L;
        status = AgentPlanExecutionStatus.IDLE;
        inputs = Map.of();
        transientAttachment = null;
        pendingSuccessorPlanId = "";
        availableSuccessorPlanIds = List.of();
        deferredSuccessorPlanId = "";
        nextActionAtMs = 0L;
        reason = "";
        sessionHandle = null;
        suspended = false;
        pendingExitMode = null;
        pendingExitReason = "";
        pendingExitDeadlineMs = 0L;
        lastOutcome = null;
        changed();
    }

    public synchronized AgentPlanCheckpoint pendingCheckpoint(int characterId, long nowMs) {
        if (characterId <= 0 || planId.isBlank() || revision == persistedRevision) {
            return null;
        }
        return new AgentPlanCheckpoint(
                2, characterId, planId, planVersion, chainId, stepIndex, stepStarted,
                stepAttempt, stepStartedAtMs,
                status, inputs, pendingSuccessorPlanId, availableSuccessorPlanIds,
                deferredSuccessorPlanId, nextActionAtMs, reason,
                sessionHandle == null ? "" : sessionHandle.sessionId(),
                sessionHandle == null ? "" : sessionHandle.requestId(),
                sessionHandle == null ? "" : sessionHandle.callerId(),
                sessionHandle == null ? 0L : sessionHandle.startedAtMs(),
                suspended, pendingExitMode, pendingExitReason, pendingExitDeadlineMs,
                revision, nowMs);
    }

    public synchronized void restore(AgentPlanCheckpoint checkpoint) {
        planId = checkpoint.planId();
        planVersion = checkpoint.planVersion();
        chainId = checkpoint.chainId();
        stepIndex = checkpoint.stepIndex();
        stepStarted = checkpoint.stepStarted();
        stepAttempt = checkpoint.stepAttempt();
        stepStartedAtMs = checkpoint.stepStartedAtMs();
        status = checkpoint.status();
        inputs = checkpoint.inputs();
        transientAttachment = null;
        pendingSuccessorPlanId = checkpoint.pendingSuccessorPlanId();
        availableSuccessorPlanIds = checkpoint.availableSuccessorPlanIds();
        deferredSuccessorPlanId = checkpoint.deferredSuccessorPlanId();
        nextActionAtMs = checkpoint.nextActionAtMs();
        reason = checkpoint.reason();
        if (!checkpoint.sessionId().isBlank()) {
            sessionHandle = new AgentPlanSessionHandle(
                    checkpoint.sessionId(), checkpoint.requestId(), checkpoint.callerId(),
                    checkpoint.characterId(), checkpoint.planId(), checkpoint.sessionStartedAtMs());
        } else {
            sessionHandle = new AgentPlanSessionHandle(
                    "restored:" + checkpoint.characterId() + ':' + checkpoint.updatedAtMs(),
                    "legacy-checkpoint:" + checkpoint.characterId(), "legacy-runtime",
                    checkpoint.characterId(), checkpoint.planId(), checkpoint.updatedAtMs());
        }
        suspended = checkpoint.suspended();
        pendingExitMode = checkpoint.pendingExitMode();
        pendingExitReason = checkpoint.pendingExitReason();
        pendingExitDeadlineMs = checkpoint.pendingExitDeadlineMs();
        lastOutcome = null;
        revision = checkpoint.stateRevision();
        persistedRevision = checkpoint.stateRevision();
    }

    public synchronized void markPersisted(long stateRevision) {
        persistedRevision = Math.max(persistedRevision, stateRevision);
    }

    private void changed() {
        revision++;
    }

    public synchronized String planId() { return planId; }
    public synchronized String planVersion() { return planVersion; }
    public synchronized String chainId() { return chainId; }
    public synchronized int stepIndex() { return stepIndex; }
    public synchronized boolean stepStartedValue() { return stepStarted; }
    public synchronized int stepAttempt() { return stepAttempt; }
    public synchronized long stepStartedAtMs() { return stepStartedAtMs; }
    public synchronized AgentPlanExecutionStatus status() { return status; }
    public synchronized Map<String, Object> inputs() { return inputs; }
    public synchronized Object transientAttachment() { return transientAttachment; }
    public synchronized String pendingSuccessorPlanId() { return pendingSuccessorPlanId; }
    public synchronized List<String> availableSuccessorPlanIds() { return availableSuccessorPlanIds; }
    public synchronized String deferredSuccessorPlanId() { return deferredSuccessorPlanId; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized String reason() { return reason; }
    public synchronized AgentPlanSessionHandle sessionHandle() { return sessionHandle; }
    public synchronized boolean suspended() { return suspended; }
    public synchronized AgentPlanExitMode pendingExitMode() { return pendingExitMode; }
    public synchronized String pendingExitReason() { return pendingExitReason; }
    public synchronized long pendingExitDeadlineMs() { return pendingExitDeadlineMs; }
    public synchronized AgentPlanOutcome lastOutcome() { return lastOutcome; }
    public synchronized boolean atStepBoundary() { return !stepStarted; }
    public synchronized AgentPlanSessionPhase phase() {
        if (status == AgentPlanExecutionStatus.ACTIVE || !pendingSuccessorPlanId.isBlank()) {
            if (pendingExitMode == AgentPlanExitMode.SUSPEND_AFTER_STEP) {
                return AgentPlanSessionPhase.SUSPENDING;
            }
            if (pendingExitMode == AgentPlanExitMode.EXIT_AFTER_STEP
                    || pendingExitMode == AgentPlanExitMode.FORCE_NOW) {
                return AgentPlanSessionPhase.DRAINING;
            }
            return suspended ? AgentPlanSessionPhase.SUSPENDED : AgentPlanSessionPhase.ACTIVE;
        }
        return switch (status) {
            case IDLE -> AgentPlanSessionPhase.IDLE;
            case SUCCEEDED -> AgentPlanSessionPhase.COMPLETED;
            case BLOCKED -> AgentPlanSessionPhase.BLOCKED;
            case FAILED -> AgentPlanSessionPhase.FAILED;
            case CANCELLED -> AgentPlanSessionPhase.CANCELLED;
            case ACTIVE -> AgentPlanSessionPhase.ACTIVE;
        };
    }
    public synchronized boolean active() {
        return status == AgentPlanExecutionStatus.ACTIVE || !pendingSuccessorPlanId.isBlank();
    }
}
