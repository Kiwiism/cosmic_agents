package server.agents.runtime.activity.session;

import java.util.Objects;

/**
 * Small world-level two-phase handoff. It requests and observes child lifecycle boundaries but
 * never advances a child runtime itself.
 */
public final class AgentActivityHandoffCoordinator {
    public Handoff begin(
            String handoffId,
            String callerId,
            AgentActivityKind targetKind,
            AgentActivitySourcePort source,
            long nowMs,
            long deadlineMs) {
        String id = required(handoffId, "handoff id");
        String caller = required(callerId, "handoff caller");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(source, "source");
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
        return new Handoff(id, caller, snapshot.agentId(), snapshot.kind(), targetKind,
                snapshot.sessionId(), Phase.REQUEST_SOURCE_EXIT, nowMs, deadlineMs, nowMs, "");
    }

    public Handoff advance(
            Handoff handoff,
            AgentActivitySourcePort source,
            AgentActivityTransferPort transfer,
            AgentActivityTargetPort target,
            long nowMs) {
        Objects.requireNonNull(handoff, "handoff");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(transfer, "transfer");
        Objects.requireNonNull(target, "target");
        if (handoff.terminal() || nowMs < handoff.updatedAtMs()) return handoff;
        if (nowMs >= handoff.deadlineMs()) {
            return handoff.transition(Phase.FAILED, nowMs, "handoff deadline expired", 0L);
        }
        if (handoff.nextActionAtMs() > nowMs) return handoff;
        return switch (handoff.phase()) {
            case REQUEST_SOURCE_EXIT -> requestSourceExit(handoff, source, nowMs);
            case WAIT_SOURCE_RELEASE -> observeSourceRelease(handoff, source, nowMs);
            case TRANSFER -> advanceTransfer(handoff, transfer, nowMs);
            case REQUEST_TARGET_ENTRY -> requestTargetEntry(handoff, target, nowMs);
            case COMPLETED, FAILED -> handoff;
        };
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
                    Phase.REQUEST_SOURCE_EXIT, nowMs, result.reason(), result.retryAtMs());
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
                "waiting for source activity boundary", nowMs);
    }

    private Handoff advanceTransfer(
            Handoff handoff, AgentActivityTransferPort transfer, long nowMs) {
        AgentActivityTransferPort.Result result = transfer.advance(nowMs);
        return switch (result.status()) {
            case READY -> handoff.transition(Phase.REQUEST_TARGET_ENTRY, nowMs, "", nowMs);
            case PENDING -> handoff.transition(
                    Phase.TRANSFER, nowMs, result.reason(), result.retryAtMs());
            case FAILED -> handoff.transition(Phase.FAILED, nowMs, result.reason(), 0L);
        };
    }

    private Handoff requestTargetEntry(
            Handoff handoff, AgentActivityTargetPort target, long nowMs) {
        AgentActivityAdmissionResult result = target.requestEntry(nowMs);
        return switch (result.status()) {
            case ACCEPTED -> {
                AgentActivitySessionSnapshot session = result.session();
                if (session.kind() != handoff.targetKind()
                        || !session.agentId().equals(handoff.agentId())) {
                    yield handoff.transition(Phase.FAILED, nowMs,
                            "destination admitted a mismatched activity session", 0L);
                }
                yield handoff.transition(Phase.COMPLETED, nowMs, "destination admitted", 0L);
            }
            case DEFERRED -> handoff.transition(
                    Phase.REQUEST_TARGET_ENTRY, nowMs, result.reason(), result.retryAtMs());
            case REJECTED -> handoff.transition(Phase.FAILED, nowMs, result.reason(), 0L);
        };
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
        COMPLETED,
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
                String reason) {
            this(handoffId, callerId, agentId, sourceKind, targetKind, sourceSessionId,
                    phase, startedAtMs, deadlineMs, updatedAtMs, updatedAtMs, reason);
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
            return phase == Phase.COMPLETED || phase == Phase.FAILED;
        }

        private Handoff transition(
                Phase next, long nowMs, String nextReason, long nextActionAtMs) {
            return new Handoff(handoffId, callerId, agentId, sourceKind, targetKind,
                    sourceSessionId, next, startedAtMs, deadlineMs, nowMs,
                    Math.max(0L, nextActionAtMs), nextReason);
        }
    }
}
