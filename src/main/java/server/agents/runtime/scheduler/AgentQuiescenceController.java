package server.agents.runtime.scheduler;

import server.agents.monitoring.AgentSchedulerMetrics;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Session-local state machine used by both legacy and central schedule handles. */
final class AgentQuiescenceController {
    enum ExecutionMode {
        NORMAL_TICK,
        FINISH_ACTIVE_FRAME,
        QUIESCENCE_MAINTENANCE,
        SKIP
    }

    private enum State {
        ACTIVE,
        REQUESTED,
        QUIESCENT,
        CLOSED
    }

    private final AgentRuntimeEntry entry;
    private final AgentSessionId sessionId;
    private final LongSupplier nowMs;
    private final IntSupplier pendingAsyncCount;
    private final BooleanSupplier activeSession;
    private State state = State.ACTIVE;
    private long nextRequestId;
    private long deadlineMs;
    private long requestedAtMs;
    private AgentQuiescenceReason reason;
    private CompletableFuture<AgentQuiescenceToken> pending;
    private AgentQuiescenceToken token;

    AgentQuiescenceController(AgentRuntimeEntry entry,
                              AgentSessionId sessionId,
                              LongSupplier nowMs,
                              IntSupplier pendingAsyncCount,
                              BooleanSupplier activeSession) {
        if (entry == null || sessionId == null || nowMs == null
                || pendingAsyncCount == null || activeSession == null) {
            throw new IllegalArgumentException("Agent quiescence dependencies are required");
        }
        this.entry = entry;
        this.sessionId = sessionId;
        this.nowMs = nowMs;
        this.pendingAsyncCount = pendingAsyncCount;
        this.activeSession = activeSession;
    }

    synchronized CompletionStage<AgentQuiescenceToken> request(AgentQuiescenceReason requestReason,
                                                                Duration timeout) {
        if (requestReason == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Agent quiescence reason and positive timeout are required");
        }
        if (state == State.CLOSED) {
            return failed(AgentQuiescenceException.Reason.CLOSED, "Agent session is closed");
        }
        if (!sessionId.matches(entry) || !activeSession.getAsBoolean()) {
            return failed(AgentQuiescenceException.Reason.STALE_SESSION, "Agent session is stale or inactive");
        }
        if (state == State.REQUESTED) {
            return pending;
        }
        if (state == State.QUIESCENT) {
            return CompletableFuture.completedFuture(token);
        }
        if (!entry.actionMailbox().beginQuiescence()) {
            return failed(AgentQuiescenceException.Reason.CLOSED, "Agent mailbox is closed");
        }
        long timeoutMs = Math.max(1L, timeout.toMillis());
        long now = nowMs.getAsLong();
        requestedAtMs = now;
        deadlineMs = saturatingAdd(now, timeoutMs);
        reason = requestReason;
        pending = new CompletableFuture<>();
        token = null;
        state = State.REQUESTED;
        AgentSchedulerMetrics.recordQuiescenceRequested();
        return pending;
    }

    ExecutionMode beforeExecution() {
        if (!checkLiveness()) {
            return ExecutionMode.SKIP;
        }
        CompletableFuture<AgentQuiescenceToken> timedOutRequest = null;
        ExecutionMode executionMode;
        synchronized (this) {
            if (state == State.CLOSED || state == State.QUIESCENT) {
                return ExecutionMode.SKIP;
            }
            if (state != State.REQUESTED) {
                return ExecutionMode.NORMAL_TICK;
            }
            if (timedOut()) {
                timedOutRequest = detachFailedRequest();
                executionMode = ExecutionMode.NORMAL_TICK;
            } else {
                executionMode = entry.tickSliceState().frameActive()
                        ? ExecutionMode.FINISH_ACTIVE_FRAME
                        : ExecutionMode.QUIESCENCE_MAINTENANCE;
            }
        }
        if (timedOutRequest != null) {
            AgentSchedulerMetrics.recordQuiescenceTimedOut();
            timedOutRequest.completeExceptionally(new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.TIMEOUT,
                    "Agent quiescence request timed out"));
        }
        return executionMode;
    }

    void runMaintenance() {
        AgentMailboxRuntime.drain(entry);
    }

    void afterExecution() {
        if (!checkLiveness()) {
            return;
        }
        CompletableFuture<AgentQuiescenceToken> completion = null;
        AgentQuiescenceToken completedToken = null;
        boolean timedOutRequest = false;
        synchronized (this) {
            if (state != State.REQUESTED) {
                return;
            }
            if (timedOut()) {
                completion = detachFailedRequest();
                timedOutRequest = true;
            } else if (!entry.tickSliceState().frameActive()
                    && pendingAsyncCount.getAsInt() == 0
                    && entry.actionMailbox().quiescenceCriticalSize() == 0) {
                token = new AgentQuiescenceToken(sessionId, ++nextRequestId, reason, nowMs.getAsLong());
                state = State.QUIESCENT;
                completion = pending;
                pending = null;
                completedToken = token;
            }
        }
        if (completion == null) {
            return;
        }
        if (timedOutRequest) {
            AgentSchedulerMetrics.recordQuiescenceTimedOut();
            completion.completeExceptionally(new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.TIMEOUT,
                    "Agent quiescence request timed out"));
        } else {
            AgentSchedulerMetrics.recordQuiescenceCompleted(
                    Math.max(0L, completedToken.completedAtMs() - requestedAtMs));
            completion.complete(completedToken);
        }
    }

    synchronized boolean resume(AgentQuiescenceToken suppliedToken) {
        if (state != State.QUIESCENT || !activeSession.getAsBoolean()
                || suppliedToken == null || !suppliedToken.equals(token)) {
            return false;
        }
        state = State.ACTIVE;
        reason = null;
        token = null;
        deadlineMs = 0L;
        requestedAtMs = 0L;
        entry.actionMailbox().endQuiescence();
        AgentSchedulerMetrics.recordQuiescenceResumed();
        return true;
    }

    synchronized boolean validates(AgentQuiescenceToken suppliedToken) {
        return state == State.QUIESCENT
                && activeSession.getAsBoolean()
                && suppliedToken != null
                && suppliedToken.equals(token);
    }

    synchronized boolean requested() {
        return state == State.REQUESTED;
    }

    synchronized boolean quiescent() {
        return state == State.QUIESCENT;
    }

    boolean checkLiveness() {
        if (activeSession.getAsBoolean()) {
            return true;
        }
        CompletableFuture<AgentQuiescenceToken> completion = null;
        synchronized (this) {
            if (state != State.REQUESTED) {
                return true;
            }
            completion = detachFailedRequest();
        }
        if (completion != null) {
            AgentSchedulerMetrics.recordQuiescenceCancelled();
            completion.completeExceptionally(new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.STALE_SESSION,
                    "Agent session became stale before quiescence completed"));
        }
        return false;
    }

    void close() {
        CompletableFuture<AgentQuiescenceToken> completion;
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            completion = pending;
            pending = null;
            state = State.CLOSED;
            token = null;
            reason = null;
            requestedAtMs = 0L;
        }
        entry.actionMailbox().endQuiescence();
        if (completion != null) {
            AgentSchedulerMetrics.recordQuiescenceCancelled();
            completion.completeExceptionally(new AgentQuiescenceException(
                    AgentQuiescenceException.Reason.CLOSED,
                    "Agent session closed before quiescence completed"));
        }
    }

    private boolean timedOut() {
        return nowMs.getAsLong() >= deadlineMs;
    }

    private CompletableFuture<AgentQuiescenceToken> detachFailedRequest() {
        CompletableFuture<AgentQuiescenceToken> completion = pending;
        pending = null;
        state = State.ACTIVE;
        token = null;
        reason = null;
        deadlineMs = 0L;
        requestedAtMs = 0L;
        entry.actionMailbox().endQuiescence();
        return completion;
    }

    private static CompletionStage<AgentQuiescenceToken> failed(
            AgentQuiescenceException.Reason reason,
            String message) {
        return CompletableFuture.failedFuture(new AgentQuiescenceException(reason, message));
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
