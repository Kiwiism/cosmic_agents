package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyReconciler;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Individual Director admission into the existing KPQ aggregate.
 *
 * <p>This seam only assembles a lobby. Once admitted, the KPQ aggregate owns formation,
 * event execution, rewards, completion, and recovery.</p>
 */
public final class AgentKpqLobbyAdmissionRuntime {
    private static final Map<String, DirectedLobby> DIRECTED = new ConcurrentHashMap<>();

    private AgentKpqLobbyAdmissionRuntime() {
    }

    public static String blocker(
            Character agent, String scenarioId, int partySize, int maximumRuns) {
        if (agent == null) return "a live Agent is required";
        if (!"kpq".equalsIgnoreCase(normalize(scenarioId))) {
            return "only the KPQ lobby is currently available";
        }
        if (partySize < AgentKpqRecruitmentPolicy.MIN_PARTY_SIZE
                || partySize > AgentKpqRecruitmentPolicy.MAX_PARTY_SIZE) {
            return "KPQ requires a party size of three or four";
        }
        if (maximumRuns != 1) {
            return "Director KPQ admission currently supports one independently owned run";
        }
        if (agent.getLevel() < AgentKpqLobbyProfile.profile().minimumLevel()
                || agent.getLevel() > AgentKpqLobbyProfile.profile().maximumLevel()) {
            return "KPQ requires level 21-30";
        }
        AgentPartyQuestEngagement existing =
                AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (existing != null && !"kpq".equals(existing.questKey())) {
            return "Agent already belongs to another party-quest engagement";
        }
        return "";
    }

    public static synchronized AgentActivityAdmissionResult requestEntry(
            AgentRuntimeEntry entry,
            Character agent,
            String scenarioId,
            int partySize,
            int maximumRuns,
            long nowMs) {
        String blocker = blocker(agent, scenarioId, partySize, maximumRuns);
        if (!blocker.isEmpty()) return AgentActivityAdmissionResult.rejected(blocker);
        if (agent.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            return AgentActivityAdmissionResult.deferred(
                    "traveling to the Kerning KPQ lobby", nowMs + 500L);
        }
        AgentPartyQuestEngagement retained =
                AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (retained != null) return accepted(agent, nowMs);

        DirectedLobby compatible = compatibleLobby(agent, partySize);
        if (compatible != null) {
            return join(compatible, agent, nowMs);
        }
        return open(entry, agent, partySize, nowMs);
    }

    public static synchronized void releaseTracking(int characterId) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement != null) DIRECTED.remove(engagement.engagementId());
    }

    public static synchronized boolean tick(int characterId, long nowMs) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null) return false;
        DirectedLobby directed = DIRECTED.get(engagement.engagementId());
        if (directed == null) return false;
        AgentPartyQuestLobbySession lobby =
                AgentPartyQuestLobbyRegistry.byId(directed.lobbyId());
        if (lobby == null || !lobby.active()) {
            DIRECTED.remove(engagement.engagementId(), directed);
            return false;
        }
        if (engagement.state() != AgentPartyQuestEngagement.State.LOBBY_FORMING) return true;
        if (!directed.claim(nowMs)) return true;

        AgentPartyQuestLobbyReconciler.Snapshot party =
                AgentPartyQuestLobbyReconciler.reconcile(lobby, nowMs);
        if (party.memberIds().size() != engagement.requestedPartySize()) return true;
        for (int memberId : party.memberIds()) {
            if (engagement.memberIds().contains(memberId)) continue;
            if (AgentRuntimeRegistry.findByAgentCharacterId(memberId) != null) {
                engagement.addDiagnostic(
                        "Another Agent must enter through the same Director KPQ lobby", nowMs);
                return true;
            }
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, memberId, AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        }
        if (!new LinkedHashSet<>(engagement.memberIds()).equals(party.memberIds())) return true;
        Character operator = character(engagement.operatorId());
        Character leader = character(party.leaderId());
        List<Character> members = party.memberIds().stream()
                .map(AgentKpqLobbyAdmissionRuntime::character)
                .filter(java.util.Objects::nonNull).toList();
        if (operator == null || leader == null || members.size() != party.memberIds().size()) {
            return true;
        }

        lobby.markReady(nowMs);
        engagement.lobbyReady(nowMs);
        boolean agentLeader = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId()) != null;
        AgentKpqAdmissionService.AdmissionResult result =
                AgentKpqAdmissionService.admitFromLobby(
                        engagement, lobby, operator, leader, members,
                        engagement.seed(), nowMs, AgentKpqSession.Mode.PRODUCTION,
                        agentLeader ? AgentKpqSession.PartyOwnership.KPQ_OWNED
                                : AgentKpqSession.PartyOwnership.EXTERNAL);
        if (result.success()) DIRECTED.remove(engagement.engagementId(), directed);
        else engagement.addDiagnostic("KPQ lobby handoff deferred: " + result.message(), nowMs);
        return true;
    }

    private static AgentActivityAdmissionResult open(
            AgentRuntimeEntry entry,
            Character agent,
            int partySize,
            long nowMs) {
        if (entry == null) return AgentActivityAdmissionResult.rejected("Agent runtime is unavailable");
        boolean createdParty = false;
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "kpq", AgentPartyQuestEngagement.Mode.PRODUCTION,
                nowMs ^ agent.getId(), agent.getId(), partySize, nowMs);
        AgentPartyQuestLobbySession lobby = null;
        try {
            AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(agent);
            if (party == null) {
                if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                    return AgentActivityAdmissionResult.rejected("could not create a KPQ lobby party");
                }
                createdParty = true;
                party = AgentPartyGatewayRuntime.party().snapshot(agent);
            }
            if (party == null || party.members().size() > partySize) {
                return AgentActivityAdmissionResult.rejected(
                        "the current party cannot fit the requested KPQ lobby size");
            }
            AgentPartyQuestEngagementRegistry.register(engagement);
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            lobby = new AgentPartyQuestLobbySession(
                    engagement.engagementId(), AgentKpqLobbyProfile.profile(),
                    engagement.seed(), agent.getId(), partySize,
                    AgentPartyQuestCandidateScope.ANY_ELIGIBLE_HUMAN, nowMs);
            boolean leader = party.members().stream().anyMatch(member -> member != null
                    && member.id() == agent.getId() && member.leader());
            int leaderId = party.members().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(server.agents.integration.AgentPartyMemberSnapshot::leader)
                    .mapToInt(server.agents.integration.AgentPartyMemberSnapshot::id)
                    .findFirst().orElse(0);
            lobby.addMember(agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                    leader ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                            : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            lobby.setCoordinatorAgentId(agent.getId());
            lobby.reconcileParty(party.id(), leaderId, party.members().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                    .collect(java.util.stream.Collectors.toSet()), nowMs);
            AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
            engagement.beginLobby(lobby.lobbyId(), nowMs);
            DIRECTED.put(engagement.engagementId(), new DirectedLobby(
                    lobby.lobbyId(), AgentClientGatewayRuntime.clients().world(agent),
                    AgentClientGatewayRuntime.clients().channel(agent), partySize));
            tick(agent.getId(), nowMs);
            return accepted(agent, nowMs);
        } catch (RuntimeException failure) {
            if (lobby != null) AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            DIRECTED.remove(engagement.engagementId());
            AgentPartyQuestEngagementRegistry.remove(engagement);
            if (createdParty && AgentPartyGatewayRuntime.party().hasParty(agent)) {
                AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            }
            return AgentActivityAdmissionResult.rejected(
                    "could not open KPQ lobby: " + failure.getMessage());
        }
    }

    private static AgentActivityAdmissionResult join(
            DirectedLobby directed, Character agent, long nowMs) {
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(directed.lobbyId());
        AgentPartyQuestEngagement engagement = lobby == null
                ? null : AgentPartyQuestEngagementRegistry.byId(lobby.engagementId());
        Character leader = lobby == null ? null : character(lobby.leaderId());
        AgentPartySnapshot party = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
        if (lobby == null || engagement == null || leader == null || party == null
                || lobby.memberIds().size() >= directed.partySize()
                || AgentPartyGatewayRuntime.party().hasParty(agent)) {
            return AgentActivityAdmissionResult.rejected("compatible KPQ lobby is no longer available");
        }
        if (!AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            return AgentActivityAdmissionResult.rejected("could not join the compatible KPQ party");
        }
        try {
            AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            AgentPartyQuestLobbyRegistry.addAndIndexMember(
                    lobby, agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                    AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            tick(agent.getId(), nowMs);
            return accepted(agent, nowMs);
        } catch (RuntimeException failure) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            AgentPartyQuestEngagementRegistry.removeAndUnindexMember(
                    engagement, agent.getId(), nowMs);
            AgentPartyQuestLobbyRegistry.removeAndUnindexMember(lobby, agent.getId(), nowMs);
            return AgentActivityAdmissionResult.rejected(
                    "could not join KPQ lobby: " + failure.getMessage());
        }
    }

    private static DirectedLobby compatibleLobby(Character agent, int partySize) {
        int world = AgentClientGatewayRuntime.clients().world(agent);
        int channel = AgentClientGatewayRuntime.clients().channel(agent);
        DIRECTED.entrySet().removeIf(entry -> {
            AgentPartyQuestLobbySession lobby =
                    AgentPartyQuestLobbyRegistry.byId(entry.getValue().lobbyId());
            return lobby == null || !lobby.active();
        });
        return DIRECTED.values().stream()
                .filter(candidate -> candidate.world() == world && candidate.channel() == channel
                        && candidate.partySize() == partySize)
                .filter(candidate -> {
                    AgentPartyQuestLobbySession lobby =
                            AgentPartyQuestLobbyRegistry.byId(candidate.lobbyId());
                    return lobby != null && lobby.active()
                            && lobby.memberIds().size() < candidate.partySize();
                }).findFirst().orElse(null);
    }

    private static AgentActivityAdmissionResult accepted(Character agent, long nowMs) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (engagement == null) {
            return AgentActivityAdmissionResult.rejected("KPQ lobby ownership was not retained");
        }
        return AgentActivityAdmissionResult.accepted(new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, AgentActivityPhase.ACTIVE,
                engagement.engagementId(), engagement.engagementId(),
                "kpq-lobby", Integer.toString(agent.getId()), engagement.startedAtMs(), ""));
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class DirectedLobby {
        private final String lobbyId;
        private final int world;
        private final int channel;
        private final int partySize;
        private long nextTickAtMs;

        private DirectedLobby(String lobbyId, int world, int channel, int partySize) {
            this.lobbyId = lobbyId;
            this.world = world;
            this.channel = channel;
            this.partySize = partySize;
        }

        private synchronized boolean claim(long nowMs) {
            if (nowMs < nextTickAtMs) return false;
            nextTickAtMs = nowMs + 500L;
            return true;
        }

        private String lobbyId() { return lobbyId; }
        private int world() { return world; }
        private int channel() { return channel; }
        private int partySize() { return partySize; }
    }
}
