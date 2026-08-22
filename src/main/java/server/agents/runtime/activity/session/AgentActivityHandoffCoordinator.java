package server.agents.runtime.activity.session;

import java.util.Objects;

/**
 * Small world-level two-phase handoff. It requests and observes child lifecycle boundaries but
 * never advances a child runtime itself.
 */
public final class AgentActivityHandoffCoordinator {
    private static final long RELEASE_OBSERVATION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.runtime.activity.session.AgentActivityHandoffCoordinator.RELEASE_OBSERVATION_RETRY_MS");

    public Handoff begin(
            String handoffId,
            String callerId,
            AgentActivityKind targetKind,
            AgentActivitySourcePort source,
            AgentActivityPreflightPort targetPreflight,
            long nowMs,
            long deadlineMs) {
        String id = required(handoffId, "handoff id");
        String caller = required(callerId, "handoff caller");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetPreflight, "targetPreflight");
        if (nowMs < 0L || deadlineMs <= nowMs) {
            throw new IllegalArgumentException("handoff requires a future deadline");
        }
        AgentActivitySessionSnapshot snapshot = source.snapshot(nowMs);
        if (snapshot == null || !snapshot.phase().ownsAgent()) {
            throw new IllegalArgumentException("handoff source must own the Agent");
        }
        if (snapshot.kind() == targetKind) {
            throw new IllegalArgumentException("handoff source and target must differ");
        }
        AgentActivityPreflightPort.Result preflight =
                targetPreflight.inspect(snapshot.agentId(), targetKind, nowMs);
        if (preflight == null) {
            throw new IllegalStateException("destination preflight returned no result");
        }
        if (!preflight.ready()) {
            return new Handoff(id, caller, snapshot.agentId(), snapshot.kind(), targetKind,
                    snapshot.sessionId(), Phase.FAILED, nowMs, deadlineMs, nowMs, false,
                    "destination preflight blocked: " + preflight.reason());
        }
        return new Handoff(id, caller, snapshot.agentId(), snapshot.kind(), targetKind,
                snapshot.sessionId(), Phase.REQUEST_SOURCE_EXIT, nowMs, deadlineMs, nowMs,
                false, "");
    }

    public Handoff advance(
            Handoff handoff,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            long nowMs) {
        return advanceInternal(handoff, source, transfer, target, null, nowMs);
    }

    public Handoff advance(
            Handoff handoff,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            AgentActivityRollbackPort rollback,
            long nowMs) {
        Objects.requireNonNull(rollback, "rollback");
        return advanceInternal(handoff, source, transfer, target, rollback, nowMs);
    }

    private Handoff advanceInternal(
            Handoff handoff,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            AgentActivityRollbackPort rollback,
            long nowMs) {
        Objects.requireNonNull(handoff, "handoff");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transfer, "transfer");
        Objects.requireNonNull(target, "target");
        if (handoff.terminal() || nowMs < handoff.updatedAtMs()) return handoff;
        if (nowMs >= handoff.deadlineMs()) {
            if (handoff.sourceReleased() && rollback != null) {
                return requestRollback(handoff, rollback, nowMs,
                        "handoff deadline expired after source release");
            }
            return handoff.transition(Phase.FAILED, nowMs,
                    handoff.sourceReleased()
                            ? "handoff deadline expired after source release; safe fallback required"
                            : "handoff deadline expired while source retained ownership",
                    0L);
        }
        if (handoff.nextActionAtMs() > nowMs) return handoff;
        return switch (handoff.phase()) {
            case REQUEST_SOURCE_EXIT -> requestSourceExit(handoff, source, nowMs);
            case WAIT_SOURCE_RELEASE -> observeSourceRelease(handoff, source, nowMs);
            case TRANSFER -> advanceTransfer(handoff, transfer, rollback, nowMs);
            case REQUEST_TARGET_ENTRY -> requestTargetEntry(handoff, target, rollback, nowMs);
            case ROLLBACK_SOURCE -> requestRollback(handoff, rollback, nowMs, handoff.reason());
            case COMPLETED, ROLLED_BACK, FAILED -> handoff;
        };
    }

    /**
     * Reconciles a restored handoff against authoritative child-session ownership. The caller must
     * restore child sessions before invoking this method.
     */
    public Handoff reconcile(
            Handoff handoff,
            AgentActivitySourcePort source,
            AgentActivitySourcePort targetObserver,
            long nowMs) {
        Objects.requireNonNull(handoff, "handoff");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetObserver, "targetObserver");
        if (handoff.terminal() || nowMs < handoff.updatedAtMs()) return handoff;
        if (nowMs >= handoff.deadlineMs()) {
            return handoff.transition(Phase.FAILED, nowMs,
                    "restored handoff deadline expired", 0L);
        }
        AgentActivitySessionSnapshot sourceState = source.snapshot(nowMs);
        AgentActivitySessionSnapshot targetState = targetObserver.snapshot(nowMs);
        boolean sourceOwns = sameSource(handoff, sourceState);
        boolean targetOwns = sameTarget(handoff, targetState);
        boolean sourceConflict = retains(sourceState) && !sourceOwns;
        boolean targetConflict = retains(targetState) && !targetOwns;
        if (sourceOwns && targetOwns) {
            return handoff.restate(Phase.FAILED, nowMs, handoff.sourceReleased(),
                    "dual foreground ownership detected during handoff restore", 0L);
        }
        if (sourceConflict || targetConflict) {
            return handoff.restate(Phase.FAILED, nowMs, handoff.sourceReleased(),
                    "conflicting foreground ownership detected during handoff restore", 0L);
        }
        if (targetOwns) {
            return handoff.restate(Phase.COMPLETED, nowMs, true,
                    "destination ownership restored", 0L);
        }
        if (sourceOwns) {
            if (handoff.phase() == Phase.ROLLBACK_SOURCE) {
                return handoff.restate(Phase.ROLLED_BACK, nowMs, true,
                        "source rollback restored", 0L);
            }
            Phase phase = handoff.phase() == Phase.WAIT_SOURCE_RELEASE
                    ? Phase.WAIT_SOURCE_RELEASE : Phase.REQUEST_SOURCE_EXIT;
            return handoff.restate(phase, nowMs, false,
                    "source ownership restored", nowMs);
        }
        Phase phase = switch (handoff.phase()) {
            case REQUEST_SOURCE_EXIT, WAIT_SOURCE_RELEASE -> Phase.TRANSFER;
            case TRANSFER, REQUEST_TARGET_ENTRY, ROLLBACK_SOURCE -> handoff.phase();
            case COMPLETED, ROLLED_BACK, FAILED -> handoff.phase();
        };
        return handoff.restate(phase, nowMs, true,
                "source release confirmed during restore", nowMs);
    }

    private Handoff requestSourceExit(
            Handoff handoff, AgentActivitySourcePort source, long nowMs) {
        AgentActivityExitResult result = source.requestGracefulExit(
                "handoff " + handoff.handoffId() + " to " + handoff.targetKind(),
                nowMs, handoff.deadlineMs());
        return switch (result.status()) {
            case RELEASED -> handoff.transition(Phase.TRANSFER, nowMs, result.reason(), nowMs);
            case REQUESTED -> handoff.transition(
                    Phase.WAIT_SOURCE_RELEASE, nowMs, result.reason(), nowMs);
            case DEFERRED -> handoff.transition(
                    Phase.REQUEST_SOURCE_EXIT, nowMs, result.reason(),
                    retryAt(handoff, nowMs, result.retryAtMs()));
            case REJECTED -> handoff.transition(Phase.FAILED, nowMs, result.reason(), 0L);
        };
    }

    private Handoff observeSourceRelease(
            Handoff handoff, AgentActivitySourcePort source, long nowMs) {
        AgentActivitySessionSnapshot snapshot = source.snapshot(nowMs);
        if (snapshot == null || !snapshot.phase().ownsAgent()) {
            return handoff.transition(Phase.TRANSFER, nowMs, "source released", nowMs);
        }
        if (!snapshot.sessionId().equals(handoff.sourceSessionId())) {
            return handoff.transition(Phase.FAILED, nowMs,
                    "source ownership changed during handoff", 0L);
        }
        return handoff.transition(Phase.WAIT_SOURCE_RELEASE, nowMs,
                "waiting for source activity boundary",
                retryAt(handoff, nowMs, nowMs + RELEASE_OBSERVATION_RETRY_MS));
    }

    private Handoff advanceTransfer(
            Handoff handoff,
            AgentActivityTransferPort transfer,
            AgentActivityRollbackPort rollback,
            long nowMs) {
        AgentActivityTransferPort.Result result = transfer.advance(nowMs);
        return switch (result.status()) {
            case READY -> handoff.transition(Phase.REQUEST_TARGET_ENTRY, nowMs, "", nowMs);
            case PENDING -> handoff.transition(
                    Phase.TRANSFER, nowMs, result.reason(),
                    retryAt(handoff, nowMs, result.retryAtMs()));
            case FAILED -> afterReleaseFailure(handoff, rollback, nowMs, result.reason());
        };
    }

    private Handoff requestTargetEntry(
            Handoff handoff,
            AgentActivityTargetPort target,
            AgentActivityRollbackPort rollback,
            long nowMs) {
        AgentActivityAdmissionResult result = target.requestEntry(nowMs);
        return switch (result.status()) {
            case ACCEPTED -> {
                AgentActivitySessionSnapshot session = result.session();
                if (session.kind() != handoff.targetKind()
                        || !session.agentId().equals(handoff.agentId())) {
                    yield afterReleaseFailure(handoff, rollback, nowMs,
                            "destination admitted a mismatched activity session");
                }
                yield handoff.transition(Phase.COMPLETED, nowMs, "destination admitted", 0L);
            }
            case DEFERRED -> handoff.transition(
                    Phase.REQUEST_TARGET_ENTRY, nowMs, result.reason(),
                    retryAt(handoff, nowMs, result.retryAtMs()));
            case REJECTED -> afterReleaseFailure(handoff, rollback, nowMs, result.reason());
        };
    }

    private Handoff afterReleaseFailure(
            Handoff handoff,
            AgentActivityRollbackPort rollback,
            long nowMs,
            String reason) {
        if (rollback == null) {
            return handoff.transition(Phase.FAILED, nowMs,
                    reason + "; source released, safe fallback required", 0L);
        }
        return handoff.transition(Phase.ROLLBACK_SOURCE, nowMs,
                reason + "; resuming source activity", nowMs);
    }

    private Handoff requestRollback(
            Handoff handoff,
            AgentActivityRollbackPort rollback,
            long nowMs,
            String failureReason) {
        if (rollback == null) {
            return handoff.transition(Phase.FAILED, nowMs,
                    failureReason + "; rollback unavailable, safe fallback required", 0L);
        }
        AgentActivityRollbackPort.Result result = rollback.requestResume(
                handoff.sourceSessionId(), nowMs);
        return switch (result.status()) {
            case RESUMED -> handoff.transition(Phase.ROLLED_BACK, nowMs,
                    failureReason + "; source resumed: " + result.reason(), 0L);
            case DEFERRED -> {
                if (nowMs >= handoff.deadlineMs()) {
                    yield handoff.transition(Phase.FAILED, nowMs,
                            failureReason + "; rollback missed deadline: " + result.reason(), 0L);
                }
                yield handoff.transition(Phase.ROLLBACK_SOURCE, nowMs,
                        failureReason + "; rollback deferred: " + result.reason(),
                        retryAt(handoff, nowMs, result.retryAtMs()));
            }
            case REJECTED -> handoff.transition(Phase.FAILED, nowMs,
                    failureReason + "; rollback rejected: " + result.reason()
                            + "; safe fallback required", 0L);
        };
    }

    private static boolean retains(AgentActivitySessionSnapshot snapshot) {
        return snapshot != null && snapshot.phase().retainsSession();
    }

    private static boolean sameSource(
            Handoff handoff, AgentActivitySessionSnapshot snapshot) {
        return retains(snapshot) && snapshot.kind() == handoff.sourceKind()
                && snapshot.agentId().equals(handoff.agentId())
                && snapshot.sessionId().equals(handoff.sourceSessionId());
    }

    private static boolean sameTarget(
            Handoff handoff, AgentActivitySessionSnapshot snapshot) {
        return retains(snapshot) && snapshot.kind() == handoff.targetKind()
                && snapshot.agentId().equals(handoff.agentId());
    }

    private static long retryAt(Handoff handoff, long nowMs, long requestedAtMs) {
        long future = Math.max(nowMs + 1L, requestedAtMs);
        return Math.min(handoff.deadlineMs(), future);
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }

    public enum Phase {
        REQUEST_SOURCE_EXIT,
        WAIT_SOURCE_RELEASE,
        TRANSFER,
        REQUEST_TARGET_ENTRY,
        ROLLBACK_SOURCE,
        COMPLETED,
        ROLLED_BACK,
        FAILED
    }

    public record Handoff(
            String handoffId,
            String callerId,
            String agentId,
            AgentActivityKind sourceKind,
            AgentActivityKind targetKind,
            String sourceSessionId,
            Phase phase,
            long startedAtMs,
            long deadlineMs,
            long updatedAtMs,
            long nextActionAtMs,
            boolean sourceReleased,
            String reason) {
        private Handoff(
                String handoffId,
                String callerId,
                String agentId,
                AgentActivityKind sourceKind,
                AgentActivityKind targetKind,
                String sourceSessionId,
                Phase phase,
                long startedAtMs,
                long deadlineMs,
                long updatedAtMs,
                boolean sourceReleased,
                String reason) {
            this(handoffId, callerId, agentId, sourceKind, targetKind, sourceSessionId,
                    phase, startedAtMs, deadlineMs, updatedAtMs, updatedAtMs,
                    sourceReleased, reason);
        }

        public Handoff {
            handoffId = required(handoffId, "handoff id");
            callerId = required(callerId, "handoff caller");
            agentId = required(agentId, "handoff Agent");
            sourceSessionId = required(sourceSessionId, "source session id");
            reason = reason == null ? "" : reason.trim();
            if (sourceKind == null || targetKind == null || phase == null
                    || startedAtMs < 0L || deadlineMs <= startedAtMs
                    || updatedAtMs < startedAtMs || nextActionAtMs < 0L) {
                throw new IllegalArgumentException("valid handoff state is required");
            }
        }

        public boolean terminal() {
            return phase == Phase.COMPLETED || phase == Phase.ROLLED_BACK
                    || phase == Phase.FAILED;
        }

        public boolean requiresSafeFallback() {
            return phase == Phase.FAILED && sourceReleased;
        }

        private Handoff transition(
                Phase next, long nowMs, String nextReason, long nextActionAtMs) {
            boolean released = sourceReleased || next == Phase.TRANSFER
                    || next == Phase.REQUEST_TARGET_ENTRY || next == Phase.COMPLETED;
            return restate(next, nowMs, released, nextReason, nextActionAtMs);
        }

        private Handoff restate(
                Phase next, long nowMs, boolean released,
                String nextReason, long nextActionAtMs) {
            return new Handoff(handoffId, callerId, agentId, sourceKind, targetKind,
                    sourceSessionId, next, startedAtMs, deadlineMs, nowMs,
                    Math.max(0L, nextActionAtMs), released, nextReason);
        }
    }
}
