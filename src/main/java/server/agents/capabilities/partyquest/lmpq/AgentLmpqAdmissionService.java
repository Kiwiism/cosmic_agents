package server.agents.capabilities.partyquest.lmpq;

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
import server.agents.runtime.simulation.AgentAbstractExecutionScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transactional shared-lobby handoff into an independent LMPQ session. */
public final class AgentLmpqAdmissionService {
    private AgentLmpqAdmissionService() { }

    public static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            Character operator, Character eventLeader, List<Character> partyMembers,
            long seed, long nowMs) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return restoreFailure(
                engagement, lobby, validation.message(), nowMs);
        if (engagement == null || lobby == null || !"lmpq".equals(lobby.profile().questKey())
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return restoreFailure(engagement, lobby,
                    "LMPQ admission requires a registered matching lobby", nowMs);
        }
        Set<Integer> expected = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> actual = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!expected.equals(actual)) return restoreFailure(
                engagement, lobby, "The party changed before LMPQ entry", nowMs);
        String blocker = AgentLmpqSessionRegistry.registrationBlocker(operator.getId(), actual);
        if (!blocker.isEmpty()) return restoreFailure(
                engagement, lobby, "LMPQ is already reserved: " + blocker, nowMs);

        long humans = validation.members().stream().filter(member -> !isAgent(member)).count();
        AgentLmpqSession.Mode mode = humans == 0 ? AgentLmpqSession.Mode.AUTONOMOUS
                : isAgent(eventLeader) ? AgentLmpqSession.Mode.HUMAN_MEMBER
                : AgentLmpqSession.Mode.HUMAN_LEADER;
        AgentLmpqSession session = new AgentLmpqSession(
                mode, seed, operator.getId(), validation.members().size(), nowMs);
        validation.members().forEach(member -> session.addMember(member.getId(), isAgent(member)
                ? AgentLmpqMemberState.MemberType.AGENT : AgentLmpqMemberState.MemberType.HUMAN));
        int executor = validation.agentMembers().getFirst().getId();
        session.setLeadership(eventLeader.getId(), executor);
        boolean published = false;
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentLmpqSessionRegistry.registerComplete(session);
            published = true;
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            validation.agentMembers().forEach(agent -> {
                var entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry != null) entry.simulationState().clearAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
            });
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "LMPQ party admitted", session);
        } catch (RuntimeException failure) {
            if (published) AgentLmpqSessionRegistry.remove(session);
            return restoreFailure(engagement, lobby, failure.getMessage(), nowMs);
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
        if (unique.size() < AgentLmpqDefinition.MIN_PARTY_SIZE
                || unique.size() > AgentLmpqDefinition.MAX_PARTY_SIZE) {
            return Validation.failure("LMPQ requires three to six members");
        }
        List<Character> agents = unique.stream().filter(AgentLmpqAdmissionService::isAgent).toList();
        if (agents.isEmpty()) return Validation.failure("Agent-assisted LMPQ requires at least one Agent");
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The LMPQ leader has no party");
        Set<Integer> requested = unique.stream().map(Character::getId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> online = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id).collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(online)) return Validation.failure("Every LMPQ member must be online");
        if (party.members().stream().noneMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader())) {
            return Validation.failure("The selected LMPQ event leader is not party leader");
        }
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            AgentPartySnapshot memberParty = AgentPartyGatewayRuntime.party().snapshot(member);
            if (memberParty == null || memberParty.id() != party.id()) {
                return Validation.failure("Every LMPQ member must share the party");
            }
            if (member.getMapId() != AgentLmpqDefinition.RECRUIT_MAP) {
                return Validation.failure(member.getName() + " is not at the LMPQ entrance");
            }
            if (member.getLevel() < AgentLmpqDefinition.MIN_LEVEL
                    || member.getLevel() > AgentLmpqDefinition.MAX_LEVEL) {
                return Validation.failure(member.getName() + " must be level 51-70");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every LMPQ member must share world and channel");
            }
            if (member.getInventory(InventoryType.ETC).getNextFreeSlot() < 0) {
                return Validation.failure(member.getName() + " needs a free ETC slot");
            }
        }
        return new Validation(true, "", List.copyOf(unique), agents);
    }

    private static boolean isAgent(Character character) {
        return character != null && AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }

    private static AdmissionResult restoreFailure(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            String message, long nowMs) {
        String reason = message == null || message.isBlank()
                ? "unknown LMPQ admission failure" : message;
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

    public record Validation(boolean success, String message, List<Character> members, List<Character> agentMembers) {
        static Validation failure(String message) { return new Validation(false, message, List.of(), List.of()); }
    }
    public record AdmissionResult(boolean success, String message, AgentLmpqSession session) {
        static AdmissionResult failure(String message) { return new AdmissionResult(false, message, null); }
    }
}
