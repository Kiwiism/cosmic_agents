package server.agents.capabilities.partyquest.epq;

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

/** Transactional EPQ lobby-to-event handoff. */
public final class AgentEpqAdmissionService {
    private AgentEpqAdmissionService() { }

    public static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            Character operator, Character eventLeader, List<Character> partyMembers,
            long seed, long nowMs, AgentEpqSession.Mode mode) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return AdmissionResult.failure(validation.message());
        if (engagement == null || lobby == null
                || !"epq".equals(lobby.profile().questKey())
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure("EPQ admission requires a registered matching EPQ lobby");
        }
        Set<Integer> expected = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> actual = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!expected.equals(actual)) return AdmissionResult.failure("The party changed before EPQ entry");

        AgentEpqSession session = new AgentEpqSession(mode, seed, operator.getId(), nowMs);
        validation.members().forEach(member -> session.addMember(member.getId(), isAgent(member)
                ? AgentEpqMemberState.MemberType.AGENT : AgentEpqMemberState.MemberType.HUMAN));
        int execution = validation.members().stream().filter(AgentEpqAdmissionService::isAgent)
                .mapToInt(Character::getId).findFirst().orElseThrow();
        session.setLeadership(eventLeader.getId(), execution);
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentEpqSessionRegistry.registerComplete(session);
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "EPQ party admitted", session);
        } catch (RuntimeException failure) {
            AgentEpqSessionRegistry.remove(session);
            return AdmissionResult.failure(failure.getMessage());
        }
    }

    public static Validation validate(Character operator, Character eventLeader, List<Character> partyMembers) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return Validation.failure("Operator, leader, and party are required");
        }
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        partyMembers.stream().filter(java.util.Objects::nonNull).forEach(member -> {
            if (unique.stream().noneMatch(existing -> existing.getId() == member.getId())) unique.add(member);
        });
        if (unique.size() < AgentEpqDefinition.MIN_PARTY_SIZE || unique.size() > AgentEpqDefinition.MAX_PARTY_SIZE) {
            return Validation.failure("EPQ requires 4-6 party members");
        }
        if (unique.stream().noneMatch(AgentEpqAdmissionService::isAgent)) {
            return Validation.failure("EPQ Agent control requires at least one Agent");
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The EPQ leader has no party");
        Set<Integer> requested = unique.stream().map(Character::getId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> online = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id).collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(online)) return Validation.failure("Every EPQ member must be online");
        if (party.members().stream().noneMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader())) {
            return Validation.failure("The selected EPQ event leader is not party leader");
        }
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            if (member.getMapId() != AgentEpqDefinition.RECRUIT_MAP) {
                return Validation.failure(member.getName() + " is not at EPQ");
            }
            if (member.getLevel() < AgentEpqDefinition.MIN_LEVEL || member.getLevel() > AgentEpqDefinition.MAX_LEVEL) {
                return Validation.failure(member.getName() + " must be level 44-55");
            }
            if (member.getJob() == null || member.getJob().getId() >= 1_000) {
                return Validation.failure(member.getName() + " must be an Adventurer");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every EPQ member must share world/channel");
            }
            if (member.getInventory(InventoryType.ETC).getNumFreeSlot() < 4
                    || member.getInventory(InventoryType.USE).getNumFreeSlot() < 1) {
                return Validation.failure(member.getName() + " needs four ETC slots and one USE slot");
            }
        }
        return new Validation(true, "", List.copyOf(unique));
    }

    private static boolean isAgent(Character character) {
        return AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }
    public record Validation(boolean success, String message, List<Character> members) {
        static Validation failure(String message) { return new Validation(false, message, List.of()); }
    }
    public record AdmissionResult(boolean success, String message, AgentEpqSession session) {
        static AdmissionResult failure(String message) { return new AdmissionResult(false, message, null); }
    }
}
