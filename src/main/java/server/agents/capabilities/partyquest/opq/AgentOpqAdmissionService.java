package server.agents.capabilities.partyquest.opq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transactional shared-lobby handoff into a six-member OPQ session. */
public final class AgentOpqAdmissionService {
    private AgentOpqAdmissionService() { }

    public static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            Character operator, Character eventLeader, List<Character> partyMembers,
            long seed, long nowMs, AgentOpqSession.Mode mode) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return AdmissionResult.failure(validation.message());
        if (engagement == null || lobby == null
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure("OPQ admission requires a registered matching lobby engagement");
        }
        Set<Integer> expected = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> actual = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!expected.equals(actual)) return AdmissionResult.failure("The party changed before OPQ entry");
        AgentOpqSession session = new AgentOpqSession(mode, seed, operator.getId(), nowMs);
        validation.members().forEach(member -> session.addMember(member.getId(), isAgent(member)
                ? AgentOpqMemberState.MemberType.AGENT : AgentOpqMemberState.MemberType.HUMAN));
        int execution = validation.members().stream().filter(AgentOpqAdmissionService::isAgent)
                .mapToInt(Character::getId).findFirst().orElseThrow();
        session.setLeadership(eventLeader.getId(), execution);
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentOpqSessionRegistry.registerComplete(session);
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "OPQ party admitted", session);
        } catch (RuntimeException failure) {
            AgentOpqSessionRegistry.remove(session);
            return AdmissionResult.failure(failure.getMessage());
        }
    }

    public static Validation validate(Character operator, Character eventLeader, List<Character> partyMembers) {
        if (operator == null || eventLeader == null || partyMembers == null) return Validation.failure("Operator, leader, and party are required");
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        partyMembers.stream().filter(java.util.Objects::nonNull).forEach(member -> {
            if (unique.stream().noneMatch(existing -> existing.getId() == member.getId())) unique.add(member);
        });
        AgentOpqRosterRequirementPolicy.Coverage coverage = AgentOpqRosterRequirementPolicy.evaluate(unique);
        if (!coverage.complete()) return Validation.failure("Missing OPQ coverage: " + String.join(", ", coverage.missingRequirements()));
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The OPQ leader has no party");
        Set<Integer> requested = unique.stream().map(Character::getId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> online = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id).collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(online)) return Validation.failure("Every OPQ member must be online");
        if (party.members().stream().noneMatch(member -> member != null && member.id() == eventLeader.getId() && member.leader())) {
            return Validation.failure("The selected OPQ event leader is not party leader");
        }
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            if (member.getMapId() != AgentOpqDefinition.RECRUIT_MAP) return Validation.failure(member.getName() + " is not at OPQ");
            if (member.getLevel() < AgentOpqDefinition.MIN_LEVEL || member.getLevel() > AgentOpqDefinition.MAX_LEVEL) {
                return Validation.failure(member.getName() + " must be level 51-70");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) return Validation.failure("Every OPQ member must share world/channel");
            if (member.getInventory(InventoryType.ETC).getNumFreeSlot() < 8) return Validation.failure(member.getName() + " needs eight free ETC slots");
        }
        return new Validation(true, "", List.copyOf(unique));
    }

    private static boolean isAgent(Character character) {
        return AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }
    public record Validation(boolean success, String message, List<Character> members) {
        static Validation failure(String message) { return new Validation(false, message, List.of()); }
    }
    public record AdmissionResult(boolean success, String message, AgentOpqSession session) {
        static AdmissionResult failure(String message) { return new AdmissionResult(false, message, null); }
    }
}
