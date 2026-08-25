package server.agents.capabilities.partyquest.hpq;

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

/** Transactional handoff from the standardized lobby into an HPQ-owned session. */
public final class AgentHpqAdmissionService {
    private AgentHpqAdmissionService() {
    }

    static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement,
            AgentPartyQuestLobbySession lobby,
            Character operator,
            Character eventLeader,
            List<Character> partyMembers,
            long seed,
            long nowMs,
            AgentHpqSession.Mode mode) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return restoreFailure(engagement, lobby, validation.message(), nowMs);
        if (engagement == null || lobby == null
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure("HPQ admission requires a registered matching lobby engagement");
        }
        Set<Integer> lobbyMembers = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> partyIds = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!lobbyMembers.equals(partyIds)) {
            return restoreFailure(engagement, lobby,
                    "The authoritative party changed before HPQ entry", nowMs);
        }
        String registryBlocker = AgentHpqSessionRegistry.registrationBlocker(
                operator.getId(), partyIds);
        if (!registryBlocker.isEmpty()) {
            return restoreFailure(engagement, lobby,
                    "HPQ admission is already reserved: " + registryBlocker, nowMs);
        }

        AgentHpqSession session = new AgentHpqSession(
                mode, seed, operator.getId(), validation.members().size(), nowMs);
        session.setPartyOwnership(isAgent(eventLeader)
                ? AgentHpqSession.PartyOwnership.HPQ_OWNED
                : AgentHpqSession.PartyOwnership.EXTERNAL);
        session.setBonusMode(isAgent(eventLeader)
                ? AgentHpqBonusPolicy.defaultMode()
                : AgentHpqSession.BonusMode.HUMAN_CHOICE);
        for (Character member : validation.members()) {
            session.addMember(member.getId(), isAgent(member)
                    ? AgentHpqMemberState.MemberType.AGENT : AgentHpqMemberState.MemberType.HUMAN);
        }
        session.setLeadership(eventLeader.getId(), validation.agentMembers().getFirst().getId());
        assignInitialRoles(session);

        boolean published = false;
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentHpqSessionRegistry.registerComplete(session);
            AgentHpqWatchdogRuntime.ensureStarted();
            published = true;
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            for (Character agent : validation.agentMembers()) {
                var entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry != null) entry.simulationState()
                        .clearAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
            }
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "HPQ party admitted", session, engagement);
        } catch (RuntimeException failure) {
            if (published) AgentHpqSessionRegistry.remove(session);
            return restoreFailure(engagement, lobby, failure.getMessage(), nowMs);
        }
    }

    private static void assignInitialRoles(AgentHpqSession session) {
        List<AgentHpqMemberState> agents = session.members().stream()
                .filter(member -> member.memberType() == AgentHpqMemberState.MemberType.AGENT)
                .toList();
        for (int index = 0; index < agents.size(); index++) {
            AgentHpqDefinition.SeedBed bed = AgentHpqDefinition.seedBeds()
                    .get(index % AgentHpqDefinition.seedBeds().size());
            agents.get(index).assign(AgentHpqMemberState.Role.SEED_COLLECTOR, bed.seedItemId());
        }
        AgentHpqMemberState leader = session.member(session.eventLeaderId());
        if (leader != null) leader.assign(AgentHpqMemberState.Role.EVENT_LEADER, 0);
    }

    static Validation validate(Character operator, Character eventLeader, List<Character> partyMembers) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return Validation.failure("Operator, leader, and party members are required");
        }
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        for (Character member : partyMembers) {
            if (member != null && unique.stream().noneMatch(existing -> existing.getId() == member.getId())) {
                unique.add(member);
            }
        }
        if (unique.size() < 3 || unique.size() > 6) {
            return Validation.failure("The current Henesys event accepts three to six members");
        }
        List<Character> agents = unique.stream().filter(AgentHpqAdmissionService::isAgent).toList();
        if (agents.isEmpty()) {
            return Validation.failure("Agent-assisted HPQ requires at least one Agent participant");
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The event leader has no party");
        Set<Integer> requestedIds = unique.stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> authoritativeIds = party.members().stream()
                .filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!requestedIds.equals(authoritativeIds)) {
            return Validation.failure("Every authoritative party member must be online for HPQ");
        }
        boolean actualLeader = party.members().stream().anyMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader());
        if (!actualLeader) return Validation.failure("The selected HPQ event leader is not the party leader");
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            AgentPartySnapshot memberParty = AgentPartyGatewayRuntime.party().snapshot(member);
            if (memberParty == null || memberParty.id() != party.id()) {
                return Validation.failure("Every HPQ member must be in the event leader's party");
            }
            if (member.getMapId() != AgentHpqDefinition.RECRUIT_MAP) {
                return Validation.failure(member.getName() + " is not at the Henesys PQ entrance");
            }
            if (member.getLevel() < 10 || member.getLevel() > 255) {
                return Validation.failure(member.getName() + " must be level 10-255 for HPQ");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every HPQ member must be on the same world and channel");
            }
            if (member.getInventory(InventoryType.ETC).getNextFreeSlot() < 0) {
                return Validation.failure(member.getName() + " needs free ETC capacity for HPQ items");
            }
        }
        return new Validation(true, "", List.copyOf(unique), agents, party);
    }

    private static AdmissionResult restoreFailure(
            AgentPartyQuestEngagement engagement,
            AgentPartyQuestLobbySession lobby,
            String message,
            long nowMs) {
        String reason = message == null || message.isBlank() ? "unknown HPQ admission failure" : message;
        if (lobby != null && lobby.active()) lobby.restoreForming(reason, nowMs);
        if (engagement != null) {
            switch (engagement.state()) {
                case LOBBY_FORMING, LOBBY_READY, RESERVING_ENTRY, ACTIVE_EVENT ->
                        engagement.restoreLobby(reason, nowMs);
                default -> engagement.addDiagnostic(reason, nowMs);
            }
        }
        return new AdmissionResult(false, reason, null, engagement);
    }

    private static boolean isAgent(Character character) {
        return character != null
                && AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }

    record Validation(boolean success, String message, List<Character> members,
                      List<Character> agentMembers, AgentPartySnapshot party) {
        private static Validation failure(String message) {
            return new Validation(false, message, List.of(), List.of(), null);
        }
    }

    record AdmissionResult(boolean success, String message, AgentHpqSession session,
                           AgentPartyQuestEngagement engagement) {
        private static AdmissionResult failure(String message) {
            return new AdmissionResult(false, message, null, null);
        }
    }
}
