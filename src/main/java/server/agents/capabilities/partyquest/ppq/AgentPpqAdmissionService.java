package server.agents.capabilities.partyquest.ppq;

import client.Character;
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

/** Transactional shared-lobby handoff into Pirate PQ. */
public final class AgentPpqAdmissionService {
    private AgentPpqAdmissionService() { }

    public static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            Character operator, Character eventLeader, List<Character> partyMembers,
            long seed, long nowMs) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) {
            return restoreFailure(engagement, lobby, validation.message(), nowMs);
        }
        if (engagement == null || lobby == null
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure(
                    "PPQ admission requires a registered matching lobby engagement");
        }
        Set<Integer> lobbyIds = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> memberIds = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!lobbyIds.equals(memberIds)) {
            return restoreFailure(engagement, lobby,
                    "The party changed before PPQ entry", nowMs);
        }
        boolean agentLeader = isAgent(eventLeader);
        boolean humanMember = validation.members().stream().anyMatch(member -> !isAgent(member));
        AgentPpqSession.Mode mode = agentLeader
                ? (humanMember ? AgentPpqSession.Mode.HUMAN_MEMBER
                        : AgentPpqSession.Mode.AUTONOMOUS)
                : AgentPpqSession.Mode.HUMAN_LEADER;
        AgentPpqSession session = new AgentPpqSession(mode, seed, operator.getId(), false, nowMs);
        validation.members().forEach(member -> session.addMember(
                member.getId(), isAgent(member)
                        ? AgentPpqMemberState.MemberType.AGENT
                        : AgentPpqMemberState.MemberType.HUMAN));
        int executor = validation.members().stream().filter(AgentPpqAdmissionService::isAgent)
                .mapToInt(Character::getId).findFirst().orElseThrow();
        session.setLeadership(eventLeader.getId(), executor);
        boolean published = false;
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentPpqSessionRegistry.registerComplete(session);
            published = true;
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "PPQ party admitted", session);
        } catch (RuntimeException failure) {
            if (published) AgentPpqSessionRegistry.remove(session);
            return restoreFailure(engagement, lobby, failure.getMessage(), nowMs);
        }
    }

    public static Validation validate(
            Character operator, Character eventLeader, List<Character> partyMembers) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return Validation.failure("Operator, leader, and party members are required");
        }
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        partyMembers.stream().filter(java.util.Objects::nonNull).forEach(member -> {
            if (unique.stream().noneMatch(existing -> existing.getId() == member.getId())) {
                unique.add(member);
            }
        });
        if (unique.size() != AgentPpqDefinition.PARTY_SIZE) {
            return Validation.failure("Agent-assisted PPQ requires six members");
        }
        if (unique.stream().noneMatch(AgentPpqAdmissionService::isAgent)) {
            return Validation.failure("Agent-assisted PPQ requires an Agent participant");
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The PPQ leader has no party");
        Set<Integer> requested = unique.stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> actual = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(actual)) return Validation.failure("Every PPQ member must be online");
        if (party.members().stream().noneMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader())) {
            return Validation.failure("The selected PPQ event leader is not party leader");
        }
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            AgentPartySnapshot memberParty = AgentPartyGatewayRuntime.party().snapshot(member);
            if (memberParty == null || memberParty.id() != party.id()) {
                return Validation.failure("Every PPQ member must share the party");
            }
            if (member.getMapId() != AgentPpqDefinition.RECRUIT_MAP) {
                return Validation.failure(member.getName() + " is not at PPQ");
            }
            if (member.getLevel() < AgentPpqLobbyProfile.profile().minimumLevel()
                    || member.getLevel() > AgentPpqLobbyProfile.profile().maximumLevel()) {
                return Validation.failure(member.getName() + " must be level "
                        + AgentPpqLobbyProfile.profile().minimumLevel() + '-'
                        + AgentPpqLobbyProfile.profile().maximumLevel());
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every PPQ member must share world and channel");
            }
        }
        return new Validation(true, "", List.copyOf(unique));
    }

    private static AdmissionResult restoreFailure(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            String message, long nowMs) {
        String reason = message == null || message.isBlank()
                ? "unknown PPQ admission failure" : message;
        if (lobby != null && lobby.active()) lobby.restoreForming(reason, nowMs);
        if (engagement != null) {
            switch (engagement.state()) {
                case LOBBY_FORMING, LOBBY_READY, RESERVING_ENTRY, ACTIVE_EVENT ->
                        engagement.restoreLobby(reason, nowMs);
                default -> engagement.addDiagnostic(reason, nowMs);
            }
        }
        return AdmissionResult.failure(reason);
    }

    private static boolean isAgent(Character character) {
        return character != null
                && AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }

    public record Validation(boolean success, String message, List<Character> members) {
        private static Validation failure(String message) {
            return new Validation(false, message, List.of());
        }
    }

    public record AdmissionResult(boolean success, String message, AgentPpqSession session) {
        private static AdmissionResult failure(String message) {
            return new AdmissionResult(false, message, null);
        }
    }
}
