package server.agents.runtime.activity.session.adapter;

import server.agents.economy.session.CommerceParticipant;
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
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

/** Standard lifecycle adapter around the economy engine's bounded session port. */
public final class EconomyActivitySessionAdapter
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final EconomySessionPort sessions;
    private final CommerceParticipant profile;
    private final EconomySessionPort.EntryRequest request;
    private UUID sessionId;
    private long startedAtMs;
    private AgentActivityPhase phase = AgentActivityPhase.IDLE;
    private String reason = "";

    public EconomyActivitySessionAdapter(
            EconomySessionPort sessions,
            CommerceParticipant profile,
            EconomySessionPort.EntryRequest request) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.request = request;
    }

    public synchronized void attach(UUID existingSessionId, long existingStartedAtMs) {
        sessionId = Objects.requireNonNull(existingSessionId, "existingSessionId");
        startedAtMs = Math.max(0L, existingStartedAtMs);
        phase = AgentActivityPhase.ACTIVE;
    }

    @Override
    public synchronized AgentActivitySessionSnapshot snapshot(long nowMs) {
        if (sessionId == null || !phase.ownsAgent()) {
            return AgentActivitySessionSnapshot.idle(AgentActivityKind.COMMERCE, profile.agentId());
        }
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.COMMERCE, phase, sessionId.toString(),
                request == null ? "" : request.requestId().toString(),
                "economy-engine", profile.agentId(), startedAtMs, reason);
    }

    @Override
    public synchronized AgentActivityExitResult requestGracefulExit(
            String exitReason, long nowMs, long deadlineMs) {
        if (sessionId == null || !phase.ownsAgent()) {
            return AgentActivityExitResult.released("economy session is not active");
        }
        EconomySessionPort.ReleaseResult result = sessions.release(
                sessionId, profile, Instant.ofEpochMilli(nowMs), exitReason);
        reason = result.reason();
        return switch (result.status()) {
            case RELEASED -> {
                phase = AgentActivityPhase.COMPLETED;
                yield AgentActivityExitResult.released(result.reason());
            }
            case DEFERRED -> {
                phase = AgentActivityPhase.DRAINING;
                yield AgentActivityExitResult.deferred(
                        result.reason(), result.retryAt().toEpochMilli());
            }
            case REJECTED -> AgentActivityExitResult.rejected(result.reason());
        };
    }

    @Override
    public synchronized AgentActivityAdmissionResult requestEntry(long nowMs) {
        if (request == null) {
            return AgentActivityAdmissionResult.rejected("economy adapter is not entry-bound");
        }
        EconomySessionPort.EntryResult result = sessions.requestEntry(
                profile, request, Instant.ofEpochMilli(nowMs));
        reason = result.reason();
        return switch (result.status()) {
            case ACCEPTED -> {
                sessionId = result.sessionId();
                startedAtMs = nowMs;
                phase = AgentActivityPhase.ACTIVE;
                yield AgentActivityAdmissionResult.accepted(snapshot(nowMs));
            }
            case DEFERRED -> AgentActivityAdmissionResult.deferred(
                    result.reason(), result.retryAt().toEpochMilli());
            case REJECTED -> AgentActivityAdmissionResult.rejected(result.reason());
        };
    }

    @Override
    public synchronized AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        if (sessionId == null || !phase.terminal()) return null;
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.COMMERCE, phase, sessionId.toString(), profile.agentId(),
                reason, phase == AgentActivityPhase.FAILED, startedAtMs,
                Math.max(startedAtMs, nowMs), Map.of());
    }
}
