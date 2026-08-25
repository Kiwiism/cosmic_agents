package server.agents.runtime.activity.session.adapter;

import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestSessionView;
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

import java.util.Map;

/** Standard lifecycle projection over the existing party-quest aggregate. */
public final class PartyQuestActivitySessionAdapter
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final int characterId;
    private final AgentActivityTargetPort entryRequest;

    public PartyQuestActivitySessionAdapter(
            int characterId, AgentActivityTargetPort entryRequest) {
        if (characterId <= 0) throw new IllegalArgumentException("valid Agent id is required");
        this.characterId = characterId;
        this.entryRequest = entryRequest;
    }

    @Override
    public AgentActivitySessionSnapshot snapshot(long nowMs) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement != null) return engagementSnapshot(engagement);
        AgentPartyQuestSessionView session = AgentPartyQuestRuntime.sessionView(characterId);
        if (session != null) return sessionSnapshot(session);
        return AgentActivitySessionSnapshot.idle(
                AgentActivityKind.PARTY_QUEST, Integer.toString(characterId));
    }

    @Override
    public AgentActivityExitResult requestGracefulExit(
            String reason, long nowMs, long deadlineMs) {
        if (!AgentPartyQuestRuntime.active(characterId)) {
            return AgentActivityExitResult.released("party quest is not active");
        }
        AgentPartyQuestSessionView session = AgentPartyQuestRuntime.sessionView(characterId);
        if (session != null) {
            if (session.phase() == AgentPartyQuestSessionView.Phase.SUSPENDED) {
                return AgentActivityExitResult.released("party quest is suspended");
            }
            if (!AgentPartyQuestRuntime.pause(characterId)) {
                return AgentActivityExitResult.deferred(
                        "party-quest session cannot pause at its current boundary",
                        Math.min(deadlineMs, nowMs + 1L));
            }
            return AgentActivityExitResult.requested(reason);
        }
        return AgentActivityExitResult.deferred(
                "party-quest lifecycle has not reached a resumable aggregate boundary",
                Math.min(deadlineMs, nowMs + 1L));
    }

    public AgentActivityRollbackPort.Result resumeExact(String sessionId, long nowMs) {
        AgentPartyQuestSessionView session = AgentPartyQuestRuntime.sessionView(characterId);
        if (session == null || !session.sessionId().equals(sessionId)) {
            return AgentActivityRollbackPort.Result.rejected("party-quest source session is not retained");
        }
        if (session.phase() != AgentPartyQuestSessionView.Phase.SUSPENDED) {
            return AgentActivityRollbackPort.Result.rejected("party-quest session is not suspended");
        }
        if (!AgentPartyQuestRuntime.resumeExact(characterId, sessionId, nowMs)) {
            return AgentActivityRollbackPort.Result.rejected("party-quest session could not resume");
        }
        return AgentActivityRollbackPort.Result.resumed("party-quest session resumed");
    }

    @Override
    public AgentActivityAdmissionResult requestEntry(long nowMs) {
        return entryRequest == null
                ? AgentActivityAdmissionResult.rejected(
                        "party-quest adapter is not entry-bound")
                : entryRequest.requestEntry(nowMs);
    }

    @Override
    public AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement != null && (engagement.state() == AgentPartyQuestEngagement.State.CLOSED
                || engagement.state() == AgentPartyQuestEngagement.State.FAILED)) {
            AgentActivityPhase phase = engagement.state() == AgentPartyQuestEngagement.State.CLOSED
                    ? AgentActivityPhase.COMPLETED : AgentActivityPhase.FAILED;
            return new AgentActivityTerminalOutcome(
                    AgentActivityKind.PARTY_QUEST, phase, engagement.engagementId(),
                    Integer.toString(characterId), engagement.failure(),
                    phase == AgentActivityPhase.FAILED, engagement.startedAtMs(),
                    Math.max(engagement.startedAtMs(), engagement.lastProgressAtMs()),
                    Map.of("questKey", engagement.questKey(),
                            "partySize", engagement.memberIds().size()));
        }
        AgentPartyQuestSessionView session = AgentPartyQuestRuntime.sessionView(characterId);
        if (session == null || !session.terminal()) return null;
        AgentActivityPhase phase = session.phase() == AgentPartyQuestSessionView.Phase.COMPLETED
                ? AgentActivityPhase.COMPLETED : AgentActivityPhase.FAILED;
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.PARTY_QUEST, phase, session.sessionId(),
                Integer.toString(characterId), session.failure(),
                phase == AgentActivityPhase.FAILED, session.startedAtMs(),
                Math.max(session.startedAtMs(), session.lastProgressAtMs()),
                Map.of("questKey", session.questKey(),
                        "partySize", session.memberCount(), "mode", session.mode()));
    }

    private AgentActivitySessionSnapshot engagementSnapshot(
            AgentPartyQuestEngagement engagement) {
        AgentActivityPhase phase = switch (engagement.state()) {
            case ACQUIRING_AGENTS, LOBBY_FORMING, LOBBY_READY, RESERVING_ENTRY,
                    ACTIVE_EVENT, POST_RUN_HOLD -> AgentActivityPhase.ACTIVE;
            case RECOVERING -> AgentActivityPhase.DRAINING;
            case CLOSED -> AgentActivityPhase.COMPLETED;
            case FAILED -> AgentActivityPhase.FAILED;
        };
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, phase, engagement.engagementId(),
                engagement.engagementId(), "pq-operator:" + engagement.operatorId(),
                Integer.toString(characterId), engagement.startedAtMs(), engagement.failure());
    }

    private AgentActivitySessionSnapshot sessionSnapshot(AgentPartyQuestSessionView session) {
        AgentActivityPhase phase = switch (session.phase()) {
            case DRAINING -> AgentActivityPhase.DRAINING;
            case COMPLETED -> AgentActivityPhase.COMPLETED;
            case FAILED -> AgentActivityPhase.FAILED;
            case SUSPENDED -> AgentActivityPhase.SUSPENDED;
            case ACTIVE -> AgentActivityPhase.ACTIVE;
        };
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, phase, session.sessionId(), session.sessionId(),
                "pq-caller:" + session.callerId(), Integer.toString(characterId), session.startedAtMs(),
                session.failure());
    }
}
