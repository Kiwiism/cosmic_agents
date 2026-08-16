package server.agents.runtime.activity.session.adapter;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.field.AgentFieldActivityRuntime;
import server.agents.runtime.field.AgentFieldActivityState;
import server.agents.runtime.field.AgentFieldAdmissionMode;
import server.agents.runtime.field.AgentFieldEntryRequest;
import server.agents.runtime.field.AgentFieldExitRequest;
import server.agents.runtime.field.AgentFieldSessionResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Standard lifecycle adapter around one exact managed field visit. */
public final class FieldActivitySessionAdapter
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final AgentRuntimeEntry entry;
    private final Character agent;
    private final AgentFieldEntryRequest request;
    private final AgentFieldAdmissionMode admissionMode;

    public FieldActivitySessionAdapter(
            AgentRuntimeEntry entry,
            Character agent,
            AgentFieldEntryRequest request,
            AgentFieldAdmissionMode admissionMode) {
        this.entry = entry;
        this.agent = agent;
        this.request = request;
        this.admissionMode = admissionMode;
    }

    @Override
    public AgentActivitySessionSnapshot snapshot(long nowMs) {
        AgentFieldActivityState.Snapshot state = entry == null ? null : entry.capabilityStates()
                .find(AgentFieldActivityState.STATE_KEY)
                .map(AgentFieldActivityState::snapshot).orElse(null);
        if (state == null || !state.active() || agent == null) {
            return AgentActivitySessionSnapshot.idle(
                    AgentActivityKind.HUNTING, agent == null ? "" : Integer.toString(agent.getId()));
        }
        AgentActivityPhase phase = switch (state.phase()) {
            case SUSPENDED -> AgentActivityPhase.SUSPENDED;
            case DRAINING -> AgentActivityPhase.DRAINING;
            case IDLE -> AgentActivityPhase.IDLE;
            case GRINDING, RESTING -> AgentActivityPhase.ACTIVE;
        };
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.HUNTING, phase, state.handle().sessionId(),
                state.handle().requestId(), state.handle().callerId(),
                Integer.toString(agent.getId()), state.handle().startedAtMs(), state.exitReason());
    }

    @Override
    public AgentActivityExitResult requestGracefulExit(
            String reason, long nowMs, long deadlineMs) {
        AgentFieldActivityState.Snapshot state = entry == null ? null : entry.capabilityStates()
                .find(AgentFieldActivityState.STATE_KEY)
                .map(AgentFieldActivityState::snapshot).orElse(null);
        if (state == null || !state.active()) {
            return AgentActivityExitResult.released("field activity is not active");
        }
        boolean accepted = AgentFieldActivityRuntime.requestExit(
                entry, agent, AgentFieldExitRequest.graceful(
                        state.handle(), reason, nowMs, deadlineMs));
        return accepted ? AgentActivityExitResult.requested(reason)
                : AgentActivityExitResult.rejected("field exit request was rejected");
    }

    @Override
    public AgentActivityAdmissionResult requestEntry(long nowMs) {
        if (entry == null || agent == null || request == null || admissionMode == null) {
            return AgentActivityAdmissionResult.rejected("field adapter is not entry-bound");
        }
        AgentFieldSessionResult result = AgentFieldActivityRuntime.requestSession(
                entry, agent, request, admissionMode, nowMs);
        if (result.started()
                || result.status() == AgentFieldSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
            return AgentActivityAdmissionResult.accepted(snapshot(nowMs));
        }
        return AgentActivityAdmissionResult.rejected(result.reason());
    }

    @Override
    public AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        server.agents.runtime.field.AgentFieldOutcome outcome =
                AgentFieldActivityRuntime.outcome(entry);
        if (outcome == null) return null;
        AgentActivityPhase phase = switch (outcome.status()) {
            case COMPLETED, EXITED -> AgentActivityPhase.COMPLETED;
            case FAILED -> AgentActivityPhase.FAILED;
            case CANCELLED -> AgentActivityPhase.CANCELLED;
        };
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("kills", outcome.kills());
        evidence.put("startingLevel", outcome.startingLevel());
        evidence.put("startingExp", outcome.startingExp());
        evidence.put("endingLevel", outcome.endingLevel());
        evidence.put("endingExp", outcome.endingExp());
        evidence.put("liveMobsAtExit", outcome.liveMobsAtExit());
        evidence.put("objectiveComplete", outcome.objectiveComplete());
        evidence.put("completedObjectiveKills", outcome.completedObjectiveKills());
        evidence.put("collectedDrops", outcome.collectedDrops());
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.HUNTING, phase, outcome.handle().sessionId(),
                Integer.toString(outcome.handle().characterId()), outcome.reason(),
                outcome.retryable(), outcome.handle().startedAtMs(),
                outcome.handle().startedAtMs() + outcome.durationMs(), evidence);
    }
}
