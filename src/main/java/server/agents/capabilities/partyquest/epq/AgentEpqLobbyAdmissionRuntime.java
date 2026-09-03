package server.agents.capabilities.partyquest.epq;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyReconciler;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestReadyGate;
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

/** EPQ-owned directed admission around the shared lobby presentation runtime. */
public final class AgentEpqLobbyAdmissionRuntime {
    private static final Map<String, DirectedLobby> DIRECTED = new ConcurrentHashMap<>();
    private AgentEpqLobbyAdmissionRuntime() { }

    public static String blocker(Character agent, String scenarioId, int partySize, int maximumRuns) {
        if (agent == null) return "a live Agent is required";
        if (!"epq".equalsIgnoreCase(normalize(scenarioId))) return "this admission policy only owns EPQ";
        if (partySize != AgentEpqRosterRequirementPolicy.PARTY_SIZE) {
            return "EPQ Agent parties require exactly five members";
        }
        if (maximumRuns != 1) return "Director EPQ admission supports one independently owned run";
        if (agent.getLevel() < AgentEpqDefinition.MIN_LEVEL || agent.getLevel() > AgentEpqDefinition.MAX_LEVEL) {
            return "EPQ requires level 44-55";
        }
        if (AgentEpqRosterRequirementPolicy.branch(agent) == null) {
            return "EPQ requires an Explorer class";
        }
        AgentPartyQuestEngagement existing = AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (existing != null && !"epq".equals(existing.questKey())) {
            return "Agent already belongs to another party-quest engagement";
        }
        return "";
    }

    public static synchronized AgentActivityAdmissionResult requestEntry(
            AgentRuntimeEntry entry, Character agent, String scenarioId,
            int partySize, int maximumRuns, long nowMs) {
        String blocker = blocker(agent, scenarioId, partySize, maximumRuns);
        if (!blocker.isEmpty()) return AgentActivityAdmissionResult.rejected(blocker);
        if (agent.getMapId() != AgentEpqDefinition.RECRUIT_MAP) {
            return AgentActivityAdmissionResult.deferred("traveling to the EPQ lobby", nowMs + 500L);
        }
        AgentPartyQuestEngagement retained = AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (retained != null) return accepted(agent);
        DirectedLobby compatible = compatibleLobby(agent, partySize);
        return compatible == null ? open(entry, agent, partySize, nowMs) : join(compatible, agent, nowMs);
    }

    public static synchronized boolean tick(int characterId, long nowMs) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null || !"epq".equals(engagement.questKey())) return false;
        DirectedLobby directed = DIRECTED.get(engagement.engagementId());
        if (directed == null) return false;
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(directed.lobbyId());
        if (lobby == null || !lobby.active()) {
            DIRECTED.remove(engagement.engagementId(), directed);
            return false;
        }
        if (engagement.state() != AgentPartyQuestEngagement.State.LOBBY_FORMING || !directed.claim(nowMs)) return true;

        AgentPartyQuestLobbyReconciler.Snapshot party = AgentPartyQuestLobbyReconciler.reconcile(lobby, nowMs);
        if (party.memberIds().size() != engagement.requestedPartySize()) return true;
        for (int memberId : party.memberIds()) {
            if (engagement.memberIds().contains(memberId)) continue;
            if (AgentRuntimeRegistry.findByAgentCharacterId(memberId) != null) {
                engagement.addDiagnostic("Another Agent must enter through the same Director EPQ lobby", nowMs);
                return true;
            }
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, memberId, AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        }
        if (!new LinkedHashSet<>(engagement.memberIds()).equals(party.memberIds())) return true;
        Character operator = character(engagement.operatorId());
        Character leader = character(party.leaderId());
        List<Character> members = party.memberIds().stream().map(AgentEpqLobbyAdmissionRuntime::character)
                .filter(java.util.Objects::nonNull).toList();
        if (operator == null || leader == null || members.size() != party.memberIds().size()) return true;

        if (!AgentPartyQuestReadyGate.ready(
                lobby.lobbyId(), lobby.rosterRevision(), engagement.seed(), nowMs)) return true;
        lobby.markReady(nowMs);
        engagement.lobbyReady(nowMs);
        AgentEpqAdmissionService.AdmissionResult result = AgentEpqAdmissionService.admitFromLobby(
                engagement, lobby, operator, leader, members, engagement.seed(), nowMs,
                AgentRuntimeRegistry.findByAgentCharacterId(leader.getId()) != null
                        ? AgentEpqSession.Mode.AUTONOMOUS
                        : AgentEpqSession.Mode.HUMAN_ASSISTED);
        if (result.success()) {
            AgentPartyQuestReadyGate.release(lobby.lobbyId());
            DIRECTED.remove(engagement.engagementId(), directed);
        }
        else engagement.addDiagnostic("EPQ lobby handoff deferred: " + result.message(), nowMs);
        return true;
    }

    public static synchronized void releaseTracking(int characterId) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement != null && "epq".equals(engagement.questKey())) DIRECTED.remove(engagement.engagementId());
    }

    private static AgentActivityAdmissionResult open(
            AgentRuntimeEntry entry, Character agent, int partySize, long nowMs) {
        if (entry == null) return AgentActivityAdmissionResult.rejected("Agent runtime is unavailable");
        boolean createdParty = false;
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "epq", AgentPartyQuestEngagement.Mode.PRODUCTION,
                nowMs ^ agent.getId(), agent.getId(), partySize, nowMs);
        AgentPartyQuestLobbySession lobby = null;
        try {
            AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(agent);
            if (party == null) {
                if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                    return AgentActivityAdmissionResult.rejected("could not create an EPQ lobby party");
                }
                createdParty = true;
                party = AgentPartyGatewayRuntime.party().snapshot(agent);
            }
            if (party == null || party.members().size() > partySize) {
                return AgentActivityAdmissionResult.rejected("the current party cannot fit the requested EPQ lobby size");
            }
            AgentPartyQuestEngagementRegistry.register(engagement);
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            lobby = new AgentPartyQuestLobbySession(
                    engagement.engagementId(), AgentEpqLobbyProfile.profile(), engagement.seed(),
                    agent.getId(), partySize, AgentPartyQuestCandidateScope.ANY_ELIGIBLE_HUMAN, nowMs);
            boolean leader = party.members().stream().anyMatch(member -> member != null
                    && member.id() == agent.getId() && member.leader());
            int leaderId = party.members().stream().filter(java.util.Objects::nonNull)
                    .filter(server.agents.integration.AgentPartyMemberSnapshot::leader)
                    .mapToInt(server.agents.integration.AgentPartyMemberSnapshot::id).findFirst().orElse(0);
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
            return accepted(agent);
        } catch (RuntimeException failure) {
            if (lobby != null) AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            DIRECTED.remove(engagement.engagementId());
            AgentPartyQuestEngagementRegistry.remove(engagement);
            if (createdParty && AgentPartyGatewayRuntime.party().hasParty(agent)) {
                AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            }
            return AgentActivityAdmissionResult.rejected("could not open EPQ lobby: " + failure.getMessage());
        }
    }

    private static AgentActivityAdmissionResult join(DirectedLobby directed, Character agent, long nowMs) {
        AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(directed.lobbyId());
        AgentPartyQuestEngagement engagement = lobby == null ? null
                : AgentPartyQuestEngagementRegistry.byId(lobby.engagementId());
        Character leader = lobby == null ? null : character(lobby.leaderId());
        AgentPartySnapshot party = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
        if (lobby == null || engagement == null || leader == null || party == null
                || lobby.memberIds().size() >= directed.partySize()
                || AgentPartyGatewayRuntime.party().hasParty(agent)) {
            return AgentActivityAdmissionResult.rejected("compatible EPQ lobby is no longer available");
        }
        if (!AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            return AgentActivityAdmissionResult.rejected("could not join the compatible EPQ party");
        }
        try {
            AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            AgentPartyQuestLobbyRegistry.addAndIndexMember(
                    lobby, agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                    AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            tick(agent.getId(), nowMs);
            return accepted(agent);
        } catch (RuntimeException failure) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            AgentPartyQuestEngagementRegistry.removeAndUnindexMember(engagement, agent.getId(), nowMs);
            AgentPartyQuestLobbyRegistry.removeAndUnindexMember(lobby, agent.getId(), nowMs);
            return AgentActivityAdmissionResult.rejected("could not join EPQ lobby: " + failure.getMessage());
        }
    }

    private static DirectedLobby compatibleLobby(Character agent, int partySize) {
        int world = AgentClientGatewayRuntime.clients().world(agent);
        int channel = AgentClientGatewayRuntime.clients().channel(agent);
        DIRECTED.entrySet().removeIf(entry -> {
            AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(entry.getValue().lobbyId());
            return lobby == null || !lobby.active();
        });
        return DIRECTED.values().stream()
                .filter(candidate -> candidate.world() == world && candidate.channel() == channel
                        && candidate.partySize() == partySize)
                .filter(candidate -> {
                    AgentPartyQuestLobbySession lobby = AgentPartyQuestLobbyRegistry.byId(candidate.lobbyId());
                    return lobby != null && lobby.active() && lobby.memberIds().size() < candidate.partySize();
                }).findFirst().orElse(null);
    }

    private static AgentActivityAdmissionResult accepted(Character agent) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(agent.getId());
        if (engagement == null || !"epq".equals(engagement.questKey())) {
            return AgentActivityAdmissionResult.rejected("EPQ lobby ownership was not retained");
        }
        return AgentActivityAdmissionResult.accepted(new AgentActivitySessionSnapshot(
                AgentActivityKind.PARTY_QUEST, AgentActivityPhase.ACTIVE,
                engagement.engagementId(), engagement.engagementId(), "epq-lobby",
                Integer.toString(agent.getId()), engagement.startedAtMs(), ""));
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }

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
