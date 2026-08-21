package server.agents.runtime.activity.session.adapter;

import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.kpq.AgentKpqRuntime;
import server.agents.capabilities.partyquest.kpq.AgentKpqSession;
import server.agents.capabilities.partyquest.kpq.AgentKpqSessionRegistry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPhase;
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
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session != null) return sessionSnapshot(session);
        return AgentActivitySessionSnapshot.idle(
                AgentActivityKind.PARTY_QUEST, Integer.toString(characterId));
    }

    @Override
    public AgentActivityExitResult requestGracefulExit(
            String reason, long nowMs, long deadlineMs) {
        if (!AgentKpqRuntime.active(characterId)) {
            return AgentActivityExitResult.released("party quest is not active");
        }
        if (AgentKpqRuntime.requestStop(characterId, reason, nowMs)) {
            return AgentKpqRuntime.active(characterId)
                    ? AgentActivityExitResult.requested(reason)
                    : AgentActivityExitResult.released(reason);
        }
        long retryAtMs = Math.max(nowMs + 1L, Math.min(deadlineMs, nowMs + 1_000L));
        return AgentActivityExitResult.deferred(
                "party quest is inside a protected active stage", retryAtMs);
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
                    Math.max(nowMs, engagement.lastProgressAtMs()),
                    Map.of("questKey", engagement.questKey(),
                            "partySize", engagement.memberIds().size()));
        }
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(characterId);
        if (session == null || (session.phase() != AgentKpqSession.Phase.COMPLETED
                && session.phase() != AgentKpqSession.Phase.FAILED)) return null;
        AgentActivityPhase phase = session.phase() == AgentKpqSession.Phase.COMPLETED
                ? AgentActivityPhase.COMPLETED : AgentActivityPhase.FAILED;
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.PARTY_QUEST, phase, session.sessionId(),
                Integer.toString(characterId), session.failure(),
                phase == AgentActivityPhase.FAILED, session.startedAtMs(),
                Math.max(nowMs, session.lastProgressAtMs()),
                Map.of("partySize", session.memberCount(), "mode", session.mode().name()));
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

    private AgentActivitySessionSnapshot sessionSnapshot(AgentKpqSession session) {
        AgentActivityPhase phase = switch (session.phase()) {
            case EXITING -> AgentActivityPhase.DRAINING;
            case COMPLETED -> AgentActivityPhase.COMPLETED;
            case FAILED -> AgentActivityPhase.FAILED;
            default -> session.paused() ? AgentActivityPhase.SUSPENDED
                    : AgentActivityPhase.ACTIVE;
        };
        int caller = session.formationCallerId() > 0
                ? session.formationCallerId() : session.operatorId();
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, phase, session.sessionId(), session.sessionId(),
                "pq-caller:" + caller, Integer.toString(characterId), session.startedAtMs(),
                session.failure());
    }
}
