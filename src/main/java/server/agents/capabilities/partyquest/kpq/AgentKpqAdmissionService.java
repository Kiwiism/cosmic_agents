package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.simulation.AgentAbstractExecutionScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transactional admission seam between a durable PQ lobby and one fresh KPQ event session. */
public final class AgentKpqAdmissionService {
    private AgentKpqAdmissionService() {
    }

    public static AdmissionResult admit(
            Character operator, Character eventLeader, List<Character> partyMembers, long seed, long nowMs) {
        return admit(operator, eventLeader, partyMembers, seed, nowMs, AgentKpqSession.Mode.PRODUCTION);
    }

    static AdmissionResult admit(
            Character operator,
            Character eventLeader,
            List<Character> partyMembers,
            long seed,
            long nowMs,
            AgentKpqSession.Mode mode) {
        AgentKpqSession.PartyOwnership ownership = mode == AgentKpqSession.Mode.BACKGROUND_POPULATION
                ? AgentKpqSession.PartyOwnership.KPQ_OWNED
                : AgentKpqSession.PartyOwnership.EXTERNAL;
        return admit(operator, eventLeader, partyMembers, seed, nowMs, mode, ownership);
    }

    /** Compatibility entry now builds the same engagement/lobby pipeline before event admission. */
    static AdmissionResult admit(
            Character operator,
            Character eventLeader,
            List<Character> partyMembers,
            long seed,
            long nowMs,
            AgentKpqSession.Mode mode,
            AgentKpqSession.PartyOwnership ownership) {
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return AdmissionResult.failure(validation.message());
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "kpq", engagementMode(mode), seed, operator.getId(), validation.members().size(), nowMs);
        AgentPartyQuestLobbySession lobby = null;
        try {
            AgentPartyQuestEngagementRegistry.register(engagement);
            for (Character agent : validation.agentMembers()) {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry == null || !AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                        "admitted to autonomous KPQ lobby", nowMs)) {
                    engagement.beginRecovery(agent.getName()
                            + " could not leave its current activity", nowMs);
                    AgentPartyQuestLifecycleRuntime.recover(engagement, nowMs);
                    return new AdmissionResult(false, agent.getName()
                            + " could not leave its current activity", null, engagement);
                }
                AgentPartyQuestEngagementRegistry.addAndIndexMember(
                        engagement, agent.getId(),
                        AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            }
            for (Character member : validation.members()) {
                if (!isAgent(member)) {
                    AgentPartyQuestEngagementRegistry.addAndIndexMember(
                            engagement, member.getId(),
                            AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
                }
            }
            lobby = new AgentPartyQuestLobbySession(
                    engagement.engagementId(), AgentKpqLobbyProfile.profile(), seed,
                    operator.getId(), validation.members().size(),
                    AgentPartyQuestCandidateScope.ANY_ELIGIBLE_HUMAN, nowMs);
            for (Character member : validation.members()) {
                lobby.addMember(member.getId(), isAgent(member)
                                ? AgentPartyQuestLobbySession.MemberType.AGENT
                                : AgentPartyQuestLobbySession.MemberType.HUMAN,
                        member.getId() == eventLeader.getId() && isAgent(member)
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            }
            lobby.setCoordinatorAgentId(validation.agentMembers().getFirst().getId());
            Set<Integer> roster = validation.members().stream()
                    .map(Character::getId).collect(java.util.stream.Collectors.toSet());
            lobby.reconcileParty(validation.party().id(), eventLeader.getId(), roster, nowMs);
            lobby.markReady(nowMs);
            engagement.beginLobby(lobby.lobbyId(), nowMs);
            engagement.lobbyReady(nowMs);
            AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
            AdmissionResult result = admitFromLobby(
                    engagement, lobby, operator, eventLeader, validation.members(),
                    seed, nowMs, mode, ownership);
            if (!result.success()) {
                AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
                engagement.beginRecovery(result.message(), nowMs);
                AgentPartyQuestLifecycleRuntime.recover(engagement, nowMs);
            }
            return result;
        } catch (RuntimeException failure) {
            if (lobby != null) AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
            engagement.beginRecovery(failure.getMessage(), nowMs);
            AgentPartyQuestLifecycleRuntime.recover(engagement, nowMs);
            return new AdmissionResult(false, failure.getMessage(), null, engagement);
        }
    }

    static AdmissionResult admitFromLobby(
            AgentPartyQuestEngagement engagement,
            AgentPartyQuestLobbySession lobby,
            Character operator,
            Character eventLeader,
            List<Character> partyMembers,
            long seed,
            long nowMs,
            AgentKpqSession.Mode mode,
            AgentKpqSession.PartyOwnership ownership) {
        AgentKpqWatchdogRuntime.ensureStarted();
        Validation validation = validate(operator, eventLeader, partyMembers);
        if (!validation.success()) return restoreFailure(engagement, lobby, validation.message(), nowMs);
        if (engagement == null || lobby == null
                || !engagement.engagementId().equals(lobby.engagementId())
                || AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) != engagement) {
            return AdmissionResult.failure("KPQ admission requires a registered matching lobby engagement");
        }
        if (!new LinkedHashSet<>(lobby.memberIds()).equals(validation.members().stream()
                .map(Character::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)))) {
            return restoreFailure(engagement, lobby,
                    "The authoritative party changed before KPQ entry", nowMs);
        }
        for (Character agent : validation.agentMembers()) {
            if (AgentPartyQuestEngagementRegistry.forMember(agent.getId()) != engagement) {
                return restoreFailure(engagement, lobby,
                        agent.getName() + " is not owned by this KPQ lobby", nowMs);
            }
        }
        String registryBlocker = AgentKpqSessionRegistry.registrationBlocker(
                operator.getId(), validation.members().stream().map(Character::getId).toList());
        if (!registryBlocker.isEmpty()) {
            return restoreFailure(engagement, lobby,
                    "KPQ admission is already reserved: " + registryBlocker, nowMs);
        }

        AgentKpqSession session = new AgentKpqSession(
                mode, seed, operator.getId(), validation.members().size(), nowMs);
        session.setPartyOwnership(ownership);
        for (Character member : validation.members()) {
            AgentKpqMemberState.MemberType memberType = isAgent(member)
                    ? AgentKpqMemberState.MemberType.AGENT : AgentKpqMemberState.MemberType.HUMAN;
            session.addMember(member.getId(), memberType);
        }
        Character executionAgent = validation.agentMembers().getFirst();
        session.setLeadership(eventLeader.getId(), executionAgent.getId());

        boolean published = false;
        try {
            lobby.reserve(nowMs);
            engagement.reserveEntry(nowMs);
            AgentKpqSessionRegistry.registerComplete(session);
            published = true;
            lobby.beginHandoff(nowMs);
            engagement.activateSession(session.sessionId(), nowMs);
            for (Character agent : validation.agentMembers()) {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry != null) entry.simulationState()
                        .clearAbstractExecution(AgentAbstractExecutionScope.TOWN_LIFE);
            }
            AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId(), nowMs);
        } catch (RuntimeException failure) {
            if (published) AgentKpqSessionRegistry.remove(session);
            return restoreFailure(engagement, lobby, failure.getMessage(), nowMs);
        }

        for (Character member : validation.members()) {
            if (isAgent(member) && member.getId() != eventLeader.getId()) {
                AgentKpqDialogue.sayMapNow(member, "Joining KPQ.");
            }
        }
        if (isAgent(eventLeader)) {
            AgentKpqDialogue.sayMapNow(eventLeader, "Party ready.");
        }
        return new AdmissionResult(true, "KPQ party admitted", session, engagement);
    }

    private static AdmissionResult restoreFailure(
            AgentPartyQuestEngagement engagement,
            AgentPartyQuestLobbySession lobby,
            String message,
            long nowMs) {
        String reason = message == null || message.isBlank() ? "unknown admission failure" : message;
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

    private static Validation validate(
            Character operator, Character eventLeader, List<Character> partyMembers) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return Validation.failure("Operator, leader, and party members are required");
        }
        List<Character> unique = new ArrayList<>();
        unique.add(eventLeader);
        for (Character member : partyMembers) if (member != null && !unique.contains(member)) unique.add(member);
        if (unique.size() < AgentKpqRecruitmentPolicy.MIN_PARTY_SIZE
                || unique.size() > AgentKpqRecruitmentPolicy.MAX_PARTY_SIZE) {
            return Validation.failure("The current Kerning event accepts three or four members");
        }
        List<Character> agents = unique.stream().filter(AgentKpqAdmissionService::isAgent).toList();
        if (agents.isEmpty()) {
            return Validation.failure("Agent-assisted KPQ requires at least one Agent execution participant");
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
            return Validation.failure("Every authoritative party member must be included and online for KPQ");
        }
        boolean actualLeader = party.members().stream().anyMatch(member -> member != null
                && member.id() == eventLeader.getId() && member.leader());
        if (!actualLeader) return Validation.failure("The selected event leader is not the party leader");
        int world = AgentClientGatewayRuntime.clients().world(eventLeader);
        int channel = AgentClientGatewayRuntime.clients().channel(eventLeader);
        for (Character member : unique) {
            AgentPartySnapshot memberParty = AgentPartyGatewayRuntime.party().snapshot(member);
            if (memberParty == null || memberParty.id() != party.id()) {
                return Validation.failure("Every KPQ member must already be in the event leader's party");
            }
            if (member.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
                return Validation.failure(member.getName() + " is not at the Kerning KPQ entrance");
            }
            if (member.getLevel() < 21 || member.getLevel() > 30) {
                return Validation.failure(member.getName() + " must be level 21-30 for KPQ");
            }
            if (AgentClientGatewayRuntime.clients().world(member) != world
                    || AgentClientGatewayRuntime.clients().channel(member) != channel) {
                return Validation.failure("Every KPQ member must be on the same world and channel");
            }
            if (!hasStageInventoryCapacity(member, member.getId() == eventLeader.getId())) {
                return Validation.failure(member.getName()
                        + " needs free Use and ETC capacity for KPQ items and rewards");
            }
        }
        return new Validation(true, "", List.copyOf(unique), agents, party, !isAgent(eventLeader));
    }

    private static boolean isAgent(Character member) {
        return member != null && AgentRuntimeRegistry.findByAgentCharacterId(member.getId()) != null;
    }

    private static AgentPartyQuestEngagement.Mode engagementMode(AgentKpqSession.Mode mode) {
        return switch (mode) {
            case PRODUCTION -> AgentPartyQuestEngagement.Mode.PRODUCTION;
            case BACKGROUND_POPULATION -> AgentPartyQuestEngagement.Mode.BACKGROUND_POPULATION;
            case TEST_OBSERVATION -> AgentPartyQuestEngagement.Mode.TEST_OBSERVATION;
        };
    }

    private static boolean hasStageInventoryCapacity(Character member, boolean leader) {
        int stageItem = leader ? AgentKpqDefinition.PASS_ITEM : AgentKpqDefinition.COUPON_ITEM;
        boolean etcCapacity = member.getItemQuantity(stageItem, false) > 0
                || member.getInventory(InventoryType.ETC).getNextFreeSlot() > -1;
        boolean rewardCapacity = member.getInventory(InventoryType.USE).getNextFreeSlot() > -1;
        return etcCapacity && rewardCapacity;
    }

    public record AdmissionResult(
            boolean success, String message, AgentKpqSession session,
            AgentPartyQuestEngagement engagement) {
        static AdmissionResult failure(String message) {
            return new AdmissionResult(false, message, null, null);
        }
    }

    private record Validation(
            boolean success,
            String message,
            List<Character> members,
            List<Character> agentMembers,
            AgentPartySnapshot party,
            boolean eventLeaderIsHuman) {
        private static Validation failure(String message) {
            return new Validation(false, message, List.of(), List.of(), null, false);
        }
    }
}
