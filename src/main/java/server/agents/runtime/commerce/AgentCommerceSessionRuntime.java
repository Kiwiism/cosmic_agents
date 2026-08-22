package server.agents.runtime.commerce;

import server.agents.economy.session.EconomySessionPort;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-Agent durable Commerce owner.
 *
 * <p>The caller owns selection and travel. This runtime owns only an admitted Commerce visit and
 * never starts Hunting, Questing, TownLife, or another Commerce visit.</p>
 */
public final class AgentCommerceSessionRuntime
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final EconomySessionPort sessions;
    private final AgentCommerceSessionStore store;
    private final AgentCommerceVisitRequest request;
    private AgentCommerceSessionCheckpoint checkpoint;

    public AgentCommerceSessionRuntime(
            EconomySessionPort sessions,
            AgentCommerceSessionStore store,
            AgentCommerceVisitRequest request) {
        this.sessions = Objects.requireNonNull(sessions, "Commerce sessions");
        this.store = Objects.requireNonNull(store, "Commerce session store");
        this.request = Objects.requireNonNull(request, "Commerce visit request");
        this.checkpoint = store.load(request.participant().agentId()).orElse(null);
        if (checkpoint != null && !checkpoint.request().requestId().equals(request.requestId())) {
            throw new IllegalStateException("a different Commerce visit is already retained for "
                    + request.participant().agentId());
        }
    }

    @Override
    public synchronized AgentActivityAdmissionResult requestEntry(long nowMs) {
        if (checkpoint != null) {
            if (checkpoint.phase().retainsSession()) {
                return AgentActivityAdmissionResult.accepted(snapshot(nowMs));
            }
            if (checkpoint.terminal()) {
                return AgentActivityAdmissionResult.rejected(
                        "previous Commerce outcome must be acknowledged");
            }
        }
        EconomySessionPort.EntryResult result = sessions.requestEntry(
                request.participant(), request.entryRequest(), Instant.ofEpochMilli(nowMs));
        return switch (result.status()) {
            case ACCEPTED -> {
                checkpoint = new AgentCommerceSessionCheckpoint(
                        AgentCommerceSessionCheckpoint.SCHEMA_VERSION, request,
                        result.sessionId().toString(), AgentActivityPhase.ACTIVE,
                        nowMs, nowMs, nowMs, result.reason());
                persist();
                yield AgentActivityAdmissionResult.accepted(snapshot(nowMs));
            }
            case DEFERRED -> AgentActivityAdmissionResult.deferred(
                    result.reason(), result.retryAt().toEpochMilli());
            case REJECTED -> AgentActivityAdmissionResult.rejected(result.reason());
        };
    }

    public synchronized boolean tick(long nowMs) {
        if (checkpoint == null || !checkpoint.phase().ownsExecution()
                || nowMs < checkpoint.revisitAtMs()) {
            return false;
        }
        if (checkpoint.phase() == AgentActivityPhase.DRAINING) {
            release("Commerce drain retry", nowMs);
            return true;
        }
        EconomySessionPort.Directive directive = sessions.performMarketCycle(
                sessionId(), request.participant(), Instant.ofEpochMilli(nowMs));
        if (directive.releaseRequested()) {
            release(directive.reason(), nowMs);
            return true;
        }
        long revisitAtMs = directive.revisitAt().map(Instant::toEpochMilli)
                .orElse(Math.addExact(nowMs, 1_000L));
        checkpoint = checkpoint(AgentActivityPhase.ACTIVE, nowMs, revisitAtMs,
                directive.reason());
        persist();
        return true;
    }

    @Override
    public synchronized AgentActivityExitResult requestGracefulExit(
            String exitReason, long nowMs, long deadlineMs) {
        if (checkpoint == null || !checkpoint.phase().retainsSession()) {
            return AgentActivityExitResult.released("Commerce session is not active");
        }
        return release(exitReason, nowMs);
    }

    @Override
    public synchronized AgentActivitySessionSnapshot snapshot(long nowMs) {
        if (checkpoint == null || !checkpoint.phase().retainsSession()) {
            return AgentActivitySessionSnapshot.idle(
                    AgentActivityKind.COMMERCE, request.participant().agentId());
        }
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.COMMERCE, checkpoint.phase(), checkpoint.sessionId(),
                request.requestId(), request.callerId(), request.participant().agentId(),
                checkpoint.startedAtMs(), checkpoint.reason());
    }

    @Override
    public synchronized AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        if (checkpoint == null || !checkpoint.terminal()) {
            return null;
        }
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.COMMERCE, checkpoint.phase(), checkpoint.sessionId(),
                request.participant().agentId(), checkpoint.reason(),
                checkpoint.phase() == AgentActivityPhase.FAILED,
                checkpoint.startedAtMs(), checkpoint.updatedAtMs(),
                Map.of("purpose", request.purpose().name(), "callerId", request.callerId()));
    }

    public synchronized void acknowledgeTerminal() {
        if (checkpoint == null || !checkpoint.terminal()) {
            throw new IllegalStateException("Commerce visit has no terminal outcome");
        }
        store.delete(request.participant().agentId());
        checkpoint = null;
    }

    public synchronized AgentCommerceSessionCheckpoint checkpoint() {
        return checkpoint;
    }

    synchronized boolean matches(AgentCommerceVisitRequest candidate) {
        return candidate != null && request.requestId().equals(candidate.requestId())
                && request.participant().agentId().equals(candidate.participant().agentId());
    }

    public synchronized AgentActivityExitResult suspendExact(String reason, long nowMs) {
        if (checkpoint == null || !checkpoint.phase().retainsSession()) {
            return AgentActivityExitResult.released("Commerce session is not active");
        }
        if (checkpoint.phase() == AgentActivityPhase.SUSPENDED) {
            return AgentActivityExitResult.released("Commerce session is suspended");
        }
        if (checkpoint.phase() == AgentActivityPhase.DRAINING) {
            return AgentActivityExitResult.rejected("Commerce session is already draining");
        }
        checkpoint = checkpoint(AgentActivityPhase.SUSPENDED, nowMs,
                checkpoint.revisitAtMs(), reason);
        persist();
        return AgentActivityExitResult.requested(reason);
    }

    public synchronized boolean resumeExact(String sessionId, long nowMs) {
        if (checkpoint == null || checkpoint.phase() != AgentActivityPhase.SUSPENDED
                || !checkpoint.sessionId().equals(sessionId)) return false;
        checkpoint = checkpoint(AgentActivityPhase.ACTIVE, nowMs, nowMs,
                "World Director rollback resumed Commerce");
        persist();
        return true;
    }

    private AgentActivityExitResult release(String reason, long nowMs) {
        EconomySessionPort.ReleaseResult result = sessions.release(
                sessionId(), request.participant(), Instant.ofEpochMilli(nowMs), reason);
        return switch (result.status()) {
            case RELEASED -> {
                checkpoint = checkpoint(AgentActivityPhase.COMPLETED, nowMs, 0L,
                        result.reason());
                persist();
                yield AgentActivityExitResult.released(result.reason());
            }
            case DEFERRED -> {
                checkpoint = checkpoint(AgentActivityPhase.DRAINING, nowMs,
                        result.retryAt().toEpochMilli(), result.reason());
                persist();
                yield AgentActivityExitResult.deferred(
                        result.reason(), result.retryAt().toEpochMilli());
            }
            case REJECTED -> {
                checkpoint = checkpoint(AgentActivityPhase.FAILED, nowMs, 0L,
                        result.reason());
                persist();
                yield AgentActivityExitResult.rejected(result.reason());
            }
        };
    }

    private AgentCommerceSessionCheckpoint checkpoint(
            AgentActivityPhase phase, long updatedAtMs, long revisitAtMs, String reason) {
        return new AgentCommerceSessionCheckpoint(
                AgentCommerceSessionCheckpoint.SCHEMA_VERSION, request,
                checkpoint.sessionId(), phase, checkpoint.startedAtMs(), updatedAtMs,
                revisitAtMs, reason);
    }

    private UUID sessionId() {
        if (checkpoint == null || checkpoint.sessionId().isEmpty()) {
            throw new IllegalStateException("Commerce session is not admitted");
        }
        return UUID.fromString(checkpoint.sessionId());
    }

    private void persist() {
        store.save(checkpoint);
    }
}
