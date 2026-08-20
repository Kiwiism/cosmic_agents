package server.agents.capabilities.partyquest.lobby;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyMemberSnapshot;
import server.agents.integration.AgentPartySnapshot;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

/** Repairs lobby state from the authoritative server party snapshot. */
public final class AgentPartyQuestLobbyReconciler {
    private AgentPartyQuestLobbyReconciler() {
    }

    public static Snapshot reconcile(AgentPartyQuestLobbySession lobby, long nowMs) {
        if (lobby == null || !lobby.active()) return Snapshot.empty();
        Character anchor = anchor(lobby);
        AgentPartySnapshot party = anchor == null ? null : AgentPartyGatewayRuntime.party().snapshot(anchor);
        if (party == null) {
            lobby.reconcileParty(0, 0, Set.of(), nowMs);
            return Snapshot.empty();
        }
        Set<Integer> actual = new LinkedHashSet<>();
        int leaderId = 0;
        for (AgentPartyMemberSnapshot member : party.members()) {
            if (member == null) continue;
            actual.add(member.id());
            if (member.leader()) leaderId = member.id();
            if (!lobby.contains(member.id())) {
                Character character = AgentPartyQuestLobbyRuntime.character(member.id());
                AgentPartyQuestLobbySession existingLobby = AgentPartyQuestLobbyRegistry.forMember(member.id());
                if (eligibleDiscoveredHuman(lobby, character)
                        && (existingLobby == null || existingLobby == lobby)) {
                    AgentPartyQuestLobbyRegistry.addAndIndexMember(
                            lobby, member.id(), AgentPartyQuestLobbySession.MemberType.HUMAN,
                            AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
                }
            }
        }
        removeDepartedHumans(lobby, actual, nowMs);
        lobby.reconcileParty(party.id(), leaderId, actual, nowMs);
        return new Snapshot(party.id(), leaderId, Set.copyOf(actual));
    }

    private static void removeDepartedHumans(
            AgentPartyQuestLobbySession lobby, Set<Integer> actual, long nowMs) {
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.byId(lobby.engagementId());
        for (AgentPartyQuestLobbySession.MemberSnapshot member : lobby.members()) {
            if (member.type() != AgentPartyQuestLobbySession.MemberType.HUMAN
                    || member.characterId() == lobby.ownerCharacterId()
                    || actual.contains(member.characterId())) continue;
            AgentPartyQuestLobbyRegistry.removeAndUnindexMember(
                    lobby, member.characterId(), nowMs);
            if (engagement != null
                    && engagement.members().get(member.characterId())
                    == AgentPartyQuestEngagement.MemberType.HUMAN) {
                AgentPartyQuestEngagementRegistry.removeAndUnindexMember(
                        engagement, member.characterId(), nowMs);
            }
        }
    }

    private static Character anchor(AgentPartyQuestLobbySession lobby) {
        Character leader = AgentPartyQuestLobbyRuntime.character(lobby.leaderId());
        if (leader != null && AgentPartyGatewayRuntime.party().hasParty(leader)) return leader;
        Character owner = AgentPartyQuestLobbyRuntime.character(lobby.ownerCharacterId());
        if (owner != null && AgentPartyGatewayRuntime.party().hasParty(owner)) return owner;
        for (int memberId : lobby.memberIds()) {
            Character member = AgentPartyQuestLobbyRuntime.character(memberId);
            if (member != null && AgentPartyGatewayRuntime.party().hasParty(member)) return member;
        }
        return null;
    }

    private static boolean eligibleDiscoveredHuman(
            AgentPartyQuestLobbySession lobby, Character candidate) {
        return candidate != null
                && AgentRuntimeRegistry.findByAgentCharacterId(candidate.getId()) == null
                && (lobby.candidateScope() == AgentPartyQuestCandidateScope.ANY_ELIGIBLE_HUMAN
                    || candidate.getId() == lobby.ownerCharacterId())
                && candidate.getMapId() == lobby.profile().mapId()
                && candidate.getLevel() >= lobby.profile().minimumLevel()
                && candidate.getLevel() <= lobby.profile().maximumLevel();
    }

    public record Snapshot(int partyId, int leaderId, Set<Integer> memberIds) {
        public Snapshot {
            memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
        }

        public static Snapshot empty() { return new Snapshot(0, 0, Set.of()); }
    }
}
