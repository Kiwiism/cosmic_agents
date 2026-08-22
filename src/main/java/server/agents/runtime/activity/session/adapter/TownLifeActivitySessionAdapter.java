package server.agents.runtime.activity.session.adapter;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionHandle;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityRollbackPort;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.townlife.AgentTownLifeTerminalState;

import java.util.Map;

/** Standard lifecycle adapter around one map-local TownLife request. */
public final class TownLifeActivitySessionAdapter
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final AgentRuntimeEntry entry;
    private final Character agent;
    private final AgentTownLifeEntryRequest request;
    private final AgentTownLifeAdmissionMode admissionMode;
    private final int identitySeed;
    private AgentActivitySessionSnapshot lastOwningSnapshot;

    public TownLifeActivitySessionAdapter(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeEntryRequest request,
            AgentTownLifeAdmissionMode admissionMode,
            int identitySeed) {
        this.entry = entry;
        this.agent = agent;
        this.request = request;
        this.admissionMode = admissionMode;
        this.identitySeed = identitySeed;
    }

    @Override
    public AgentActivitySessionSnapshot snapshot(long nowMs) {
        AgentTownLifeState state = entry == null ? null : entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        if (state == null || !state.enabled() || agent == null) {
            return AgentActivitySessionSnapshot.idle(
                    AgentActivityKind.TOWN_LIFE, agent == null ? "" : Integer.toString(agent.getId()));
        }
        AgentActivityPhase phase = state.externalInteractionPaused()
                ? AgentActivityPhase.SUSPENDED
                : state.exitRequested() ? AgentActivityPhase.DRAINING : AgentActivityPhase.ACTIVE;
        lastOwningSnapshot = new AgentActivitySessionSnapshot(
                AgentActivityKind.TOWN_LIFE, phase, state.sessionId(), state.requestId(),
                state.callerId(), Integer.toString(agent.getId()), state.sessionStartedAtMs(),
                state.exitReason());
        return lastOwningSnapshot;
    }

    @Override
    public AgentActivityExitResult requestGracefulExit(
            String reason, long nowMs, long deadlineMs) {
        AgentTownLifeState state = entry == null ? null : entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        AgentTownLifeSessionHandle handle = state == null || agent == null
                ? null : state.sessionHandle(agent.getId());
        if (handle == null) return AgentActivityExitResult.released("TownLife is not active");
        if (state.externalInteractionPaused()) {
            return AgentActivityExitResult.released("TownLife is suspended");
        }
        if (state.hasCommittedActivity()) {
            return AgentActivityExitResult.deferred(
                    "TownLife is finishing its current activity", Math.min(deadlineMs, nowMs + 1L));
        }
        AgentTownLifeRuntime.suspendForExternalInteraction(entry, agent, nowMs);
        return AgentActivityExitResult.requested(reason);
    }

    public AgentActivityRollbackPort.Result resumeExact(String sessionId, long nowMs) {
        AgentTownLifeState state = entry == null ? null : entry.capabilityStates()
                .find(AgentTownLifeState.STATE_KEY).orElse(null);
        if (state == null || !state.enabled() || !state.sessionId().equals(sessionId)) {
            return AgentActivityRollbackPort.Result.rejected("TownLife source session is not retained");
        }
        if (!state.externalInteractionPaused()) {
            return AgentActivityRollbackPort.Result.rejected("TownLife session is not suspended");
        }
        AgentTownLifeRuntime.resumeAfterExternalInteraction(entry, nowMs);
        return AgentActivityRollbackPort.Result.resumed("TownLife session resumed");
    }

    @Override
    public AgentActivityAdmissionResult requestEntry(long nowMs) {
        if (entry == null || agent == null || request == null || admissionMode == null) {
            return AgentActivityAdmissionResult.rejected("TownLife adapter is not entry-bound");
        }
        AgentTownLifeSessionResult result = AgentTownLifeRuntime.requestSession(
                entry, agent, request, admissionMode, nowMs, identitySeed);
        if (result.started()) {
            return AgentActivityAdmissionResult.accepted(snapshot(nowMs));
        }
        return AgentActivityAdmissionResult.rejected(result.reason());
    }

    @Override
    public AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        if (entry == null || lastOwningSnapshot == null) return null;
        AgentTownLifeTerminalState.Snapshot terminal = entry.capabilityStates()
                .require(AgentTownLifeTerminalState.STATE_KEY).snapshot();
        if (!terminal.matches(lastOwningSnapshot.sessionId())) return null;
        AgentActivityPhase phase = switch (terminal.phase()) {
            case EXITED -> AgentActivityPhase.COMPLETED;
            case TIMED_OUT -> AgentActivityPhase.FAILED;
            case FORCED -> AgentActivityPhase.CANCELLED;
            default -> throw new IllegalStateException("non-terminal TownLife outcome");
        };
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.TOWN_LIFE, phase, terminal.sessionId(),
                lastOwningSnapshot.agentId(), terminal.reason(),
                phase == AgentActivityPhase.FAILED,
                lastOwningSnapshot.startedAtMs(), terminal.occurredAtMs(), Map.of());
    }
}
