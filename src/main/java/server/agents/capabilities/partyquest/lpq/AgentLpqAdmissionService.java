package server.agents.capabilities.partyquest.lpq;

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

/** Transactional standardized-lobby handoff into an LPQ-owned session. */
public final class AgentLpqAdmissionService {
    private AgentLpqAdmissionService() { }

    public static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement, AgentPartyQuestLobbySession lobby,
            Character operator, Character eventLeader, List<Character> partyMembers,
            long seed, long nowMs, AgentLpqSession.Mode mode, int preferredHumanId,
            AgentLpqSession.HumanRolePreference humanRolePreference) {
        Validation validation = validate(operator, eventLeader, partyMembers,
                preferredHumanId, humanRolePreference);
        if (!validation.success()) return restoreFailure(engagement, lobby, validation.message(), nowMs);
        if (engagement == null || lobby == null
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure("LPQ admission requires a registered matching lobby engagement");
        }
        Set<Integer> lobbyMembers = new LinkedHashSet<>(lobby.memberIds());
        Set<Integer> partyIds = validation.members().stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!lobbyMembers.equals(partyIds)) {
            return restoreFailure(engagement, lobby, "The party changed before LPQ entry", nowMs);
        }
        String blocker = AgentLpqSessionRegistry.registrationBlocker(operator.getId(), partyIds);
        if (!blocker.isEmpty()) return restoreFailure(engagement, lobby, "LPQ is reserved: " + blocker, nowMs);

        AgentLpqSession session = new AgentLpqSession(mode, seed, operator.getId(), partyIds.size(), nowMs);
        session.setPartyOwnership(isAgent(eventLeader)
                ? AgentLpqSession.PartyOwnership.LPQ_OWNED : AgentLpqSession.PartyOwnership.EXTERNAL);
        session.setBonusMode(isAgent(eventLeader)
                ? AgentLpqSession.BonusMode.ENTER : AgentLpqSession.BonusMode.HUMAN_CHOICE);
        validation.members().forEach(member -> session.addMember(member.getId(), isAgent(member)
                ? AgentLpqMemberState.MemberType.AGENT : AgentLpqMemberState.MemberType.HUMAN));
        if (preferredHumanId > 0 && validation.members().stream().anyMatch(member ->
                member.getId() == preferredHumanId && !isAgent(member))) {
            session.setHumanRolePreference(preferredHumanId,
                    humanRolePreference == null
                            ? AgentLpqSession.HumanRolePreference.DEFAULT : humanRolePreference);
        }
        session.setLeadership(eventLeader.getId(), validation.agentMembers().getFirst().getId());
        boolean published = false;
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentLpqSessionRegistry.registerComplete(session);
            AgentLpqWatchdogRuntime.ensureStarted();
            published = true;
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            validation.agentMembers().forEach(agent -> {
                var entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry != null) entry.simulationState().clearAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
            });
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            return new AdmissionResult(true, "LPQ party admitted", session);
        } catch (RuntimeException failure) {
            if (published) AgentLpqSessionRegistry.remove(session);
            return restoreFailure(engagement, lobby, failure.getMessage(), nowMs);
        }
    }

    public static Validation validate(Character operator, Character eventLeader, List<Character> partyMembers) {
        return validate(operator, eventLeader, partyMembers, 0,
                AgentLpqSession.HumanRolePreference.DEFAULT);
    }

    static Validation validate(Character operator, Character eventLeader, List<Character> partyMembers,
                               int preferredHumanId,
                               AgentLpqSession.HumanRolePreference humanRolePreference) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return Validation.failure("Operator, leader, and party members are required");
        }
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        partyMembers.stream().filter(java.util.Objects::nonNull).forEach(member -> {
            if (unique.stream().noneMatch(existing -> existing.getId() == member.getId())) unique.add(member);
        });
        if (unique.size() != AgentLpqDefinition.RECOMMENDED_PARTY_SIZE) {
            return Validation.failure("Agent-assisted LPQ currently requires six members; the ordinary event still accepts five to six");
        }
        List<Character> agents = unique.stream().filter(AgentLpqAdmissionService::isAgent).toList();
        if (agents.isEmpty()) return Validation.failure("Agent-assisted LPQ requires an Agent participant");
        AgentLpqRosterRequirementPolicy.Coverage coverage =
                capabilityCoverage(unique, agents, preferredHumanId, humanRolePreference);
        if (!coverage.complete()) return Validation.failure("Missing LPQ party capability: "
                + String.join(", ", coverage.missingRequirements()));
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (party == null) return Validation.failure("The LPQ leader has no party");
        Set<Integer> requested = unique.stream().map(Character::getId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> actual = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id).collect(java.util.stream.Collectors.toSet());
        if (!requested.equals(actual)) return Validation.failure("Every party member must be online for LPQ");
        if (party.members().stream().noneMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader())) {
            return Validation.failure("The selected LPQ event leader is not the party leader");
        }
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            AgentPartySnapshot memberParty = AgentPartyGatewayRuntime.party().snapshot(member);
            if (memberParty == null || memberParty.id() != party.id()) return Validation.failure("Every LPQ member must share the party");
            if (member.getMapId() != AgentLpqDefinition.RECRUIT_MAP) return Validation.failure(member.getName() + " is not at LPQ");
            if (member.getLevel() < AgentLpqDefinition.MIN_LEVEL || member.getLevel() > AgentLpqDefinition.MAX_LEVEL) {
                return Validation.failure(member.getName() + " must be level 35-50");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every LPQ member must share world and channel");
            }
            if (member.getInventory(InventoryType.ETC).getNextFreeSlot() < 0) {
                return Validation.failure(member.getName() + " needs a free ETC slot");
            }
        }
        return new Validation(true, "", List.copyOf(unique), agents);
    }

    static AgentLpqRosterRequirementPolicy.Coverage capabilityCoverage(
            List<Character> partyMembers, List<Character> agentMembers, int preferredHumanId,
            AgentLpqSession.HumanRolePreference humanRolePreference) {
        List<Character> roster = new ArrayList<>(agentMembers);
        AgentLpqSession.HumanRolePreference preference = humanRolePreference == null
                ? AgentLpqSession.HumanRolePreference.DEFAULT : humanRolePreference;
        if (preference != AgentLpqSession.HumanRolePreference.DEFAULT) {
            partyMembers.stream().filter(member -> member != null
                            && member.getId() == preferredHumanId && !isAgent(member)
                            && supports(member, preference))
                    .findFirst().ifPresent(roster::add);
        }
        return AgentLpqRosterRequirementPolicy.evaluate(roster);
    }

    private static boolean supports(Character character,
                                    AgentLpqSession.HumanRolePreference preference) {
        return switch (preference) {
            case TELEPORT -> AgentLpqRosterRequirementPolicy.teleportMagic(character);
            case DARK_SIGHT -> AgentLpqRosterRequirementPolicy.darkSight(character);
            case RANGED -> AgentLpqRosterRequirementPolicy.rangedAttack(character);
            case DEFAULT -> false;
        };
    }

    private static AdmissionResult restoreFailure(AgentPartyQuestEngagement engagement,
                                                   AgentPartyQuestLobbySession lobby,
                                                   String message, long nowMs) {
        String reason = message == null || message.isBlank() ? "unknown LPQ admission failure" : message;
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
        return character != null && AgentRuntimeRegistry.findByAgentCharacterId(character.getId()) != null;
    }

    public record Validation(boolean success, String message, List<Character> members,
                             List<Character> agentMembers) {
        private static Validation failure(String message) { return new Validation(false, message, List.of(), List.of()); }
    }
    public record AdmissionResult(boolean success, String message, AgentLpqSession session) {
        private static AdmissionResult failure(String message) { return new AdmissionResult(false, message, null); }
    }
}
