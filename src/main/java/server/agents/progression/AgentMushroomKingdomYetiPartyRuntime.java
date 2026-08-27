package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

/** Exact-progress matchmaking for the three King Pepe and Yeti variants. */
final class AgentMushroomKingdomYetiPartyRuntime {
    static final int LOBBY_MAP_ID = 106_021_400;
    static final int MAX_PARTY_SIZE = 3;

    enum Decision { READY_LEADER, WAITING }
    enum Mode { STORY, FARM }

    record Progress(int firstYeti, int secondYeti, int thirdYeti) {
    }

    private AgentMushroomKingdomYetiPartyRuntime() {
    }

    static Decision prepare(Character agent,
                            AgentMushroomKingdomYetiLobbyState state,
                            PrimitiveCapabilityGateway gateway,
                            long nowMs,
                            long agentScanMs,
                            long humanResponseMs) {
        return prepare(agent, state, gateway, Mode.STORY, nowMs, agentScanMs, humanResponseMs);
    }

    static Decision prepare(Character agent,
                            AgentMushroomKingdomYetiLobbyState state,
                            PrimitiveCapabilityGateway gateway,
                            Mode mode,
                            long nowMs,
                            long agentScanMs,
                            long humanResponseMs) {
        PartyGateway parties = AgentPartyGatewayRuntime.party();
        Progress progress = mode == Mode.STORY ? progress(agent, gateway) : new Progress(0, 0, 0);
        List<Character> compatible = compatibleAgents(agent).stream()
                .filter(candidate -> eligible(candidate, gateway, mode, progress))
                .toList();
        List<Character> humans = compatibleHumans(agent, parties, gateway, progress, mode);
        return prepare(agent, state, gateway, parties, compatible, humans, progress, mode,
                nowMs, agentScanMs, humanResponseMs);
    }

    static Decision prepare(Character agent,
                            AgentMushroomKingdomYetiLobbyState state,
                            PrimitiveCapabilityGateway gateway,
                            PartyGateway parties,
                            List<Character> compatible,
                            List<Character> humans,
                            Progress progress,
                            long nowMs,
                            long agentScanMs,
                            long humanResponseMs) {
        return prepare(agent, state, gateway, parties, compatible, humans, progress, Mode.STORY,
                nowMs, agentScanMs, humanResponseMs);
    }

    static Decision prepare(Character agent,
                            AgentMushroomKingdomYetiLobbyState state,
                            PrimitiveCapabilityGateway gateway,
                            PartyGateway parties,
                            List<Character> compatible,
                            List<Character> humans,
                            Progress progress,
                            Mode mode,
                            long nowMs,
                            long agentScanMs,
                            long humanResponseMs) {
        state.beginYetiLobbyVisit(nowMs);
        AgentPartySnapshot party = parties.snapshot(agent);
        if (party != null && !validParty(agent, progress, party, parties, gateway, mode)) {
            cancelPendingHumanInvites(state, parties);
            parties.leaveCurrentParty(agent);
            party = null;
            state.restartYetiLobbyVisit(nowMs);
        }

        boolean waitingForHuman = !state.yetiHumanInviteeIds().isEmpty();
        if (!waitingForHuman) {
            if (party == null) {
                joinOpenCompatibleParty(agent, progress, parties, gateway, compatible, mode);
                party = parties.snapshot(agent);
            }
            if (party == null) {
                createCompatibleParty(parties, compatible);
                party = parties.snapshot(agent);
            }
            if (party != null && leaderId(party) == agent.getId()) {
                fillCompatibleParty(agent, party, parties, compatible);
                party = parties.snapshot(agent);
            }
        }

        int size = party == null ? 1 : party.members().size();
        boolean leader = party == null || leaderId(party) == agent.getId();
        if (size >= MAX_PARTY_SIZE) {
            cancelPendingHumanInvites(state, parties);
            state.completeYetiMatchmaking();
            return leader ? Decision.READY_LEADER : Decision.WAITING;
        }
        if (state.yetiMatchmakingComplete()) {
            return leader ? Decision.READY_LEADER : Decision.WAITING;
        }
        if (!leader || !state.yetiAgentScanExpired(nowMs, agentScanMs)) return Decision.WAITING;

        List<Integer> invitedIds = state.yetiHumanInviteeIds();
        if (!invitedIds.isEmpty()) {
            boolean responseWindowExpired = state.yetiHumanInviteResponseExpired(nowMs, humanResponseMs);
            boolean responseStillPending = invitedIds.stream()
                    .anyMatch(parties::hasPendingPartyInvite);
            if (!responseWindowExpired && responseStillPending) return Decision.WAITING;
            cancelPendingHumanInvites(state, parties);
            state.completeYetiMatchmaking();
            return Decision.READY_LEADER;
        }

        int prospectiveOpenSlots = party == null
                ? MAX_PARTY_SIZE - 1
                : MAX_PARTY_SIZE - party.members().size();
        List<Character> inviteCandidates = humans.stream()
                .filter(candidate -> candidate != null && !parties.hasParty(candidate))
                .sorted(Comparator.comparingInt(Character::getId))
                .limit(Math.max(0, prospectiveOpenSlots))
                .toList();
        if (inviteCandidates.isEmpty()) {
            state.completeYetiMatchmaking();
            return Decision.READY_LEADER;
        }
        if (party == null) {
            if (!parties.createAgentParty(agent)) {
                state.completeYetiMatchmaking();
                return Decision.READY_LEADER;
            }
            party = parties.snapshot(agent);
        }
        int openSlots = party == null ? 0 : MAX_PARTY_SIZE - party.members().size();
        List<Integer> sentInvites = new ArrayList<>();
        inviteCandidates.stream()
                .limit(Math.max(0, openSlots))
                .forEach(candidate -> {
                    if (parties.invitePartyMember(agent, candidate)) sentInvites.add(candidate.getId());
                });
        if (sentInvites.isEmpty()) {
            state.completeYetiMatchmaking();
            return Decision.READY_LEADER;
        }
        state.markYetiHumanInvites(sentInvites, nowMs);
        return Decision.WAITING;
    }

    static void leaveLobby(AgentMushroomKingdomYetiLobbyState state) {
        if (state == null) return;
        cancelPendingHumanInvites(state, AgentPartyGatewayRuntime.party());
        state.clearYetiLobbyVisit();
    }

    static Progress progress(Character agent, PrimitiveCapabilityGateway gateway) {
        return new Progress(
                Math.min(1, gateway.questProgress(agent, 2330, 3300005)),
                Math.min(1, gateway.questProgress(agent, 2330, 3300006)),
                Math.min(1, gateway.questProgress(agent, 2330, 3300007)));
    }

    private static void joinOpenCompatibleParty(Character agent,
                                                Progress progress,
                                                PartyGateway parties,
                                                PrimitiveCapabilityGateway gateway,
                                                List<Character> compatible,
                                                Mode mode) {
        compatible.stream()
                .filter(other -> other != agent)
                .map(parties::snapshot)
                .filter(snapshot -> snapshot != null
                        && snapshot.members().size() < MAX_PARTY_SIZE
                        && leaderId(snapshot) > 0)
                .sorted(Comparator.comparingInt(AgentMushroomKingdomYetiPartyRuntime::leaderId))
                .filter(snapshot -> {
                    Character leader = compatible.stream()
                            .filter(other -> other.getId() == leaderId(snapshot))
                            .findFirst().orElse(null);
                    return leader != null && validParty(
                            leader, progress, snapshot, parties, gateway, mode);
                })
                .findFirst()
                .ifPresent(snapshot -> parties.joinAgentParty(agent, snapshot.id()));
    }

    private static void createCompatibleParty(PartyGateway parties,
                                              List<Character> compatible) {
        List<Character> unpartied = compatible.stream()
                .filter(candidate -> parties.snapshot(candidate) == null)
                .sorted(Comparator.comparingInt(Character::getId))
                .limit(MAX_PARTY_SIZE)
                .toList();
        if (unpartied.size() < 2) return;
        Character leader = unpartied.getFirst();
        if (!parties.createAgentParty(leader)) return;
        AgentPartySnapshot party = parties.snapshot(leader);
        if (party == null) return;
        for (Character member : unpartied.subList(1, unpartied.size())) {
            parties.joinAgentParty(member, party.id());
        }
    }

    private static void fillCompatibleParty(Character leader,
                                             AgentPartySnapshot party,
                                             PartyGateway parties,
                                             List<Character> compatible) {
        int openSlots = MAX_PARTY_SIZE - party.members().size();
        if (openSlots <= 0) return;
        compatible.stream()
                .filter(candidate -> candidate != leader && parties.snapshot(candidate) == null)
                .sorted(Comparator.comparingInt(Character::getId))
                .limit(openSlots)
                .forEach(candidate -> parties.joinAgentParty(candidate, party.id()));
    }

    private static boolean validParty(Character anchor,
                                      Progress progress,
                                      AgentPartySnapshot snapshot,
                                      PartyGateway parties,
                                      PrimitiveCapabilityGateway gateway,
                                      Mode mode) {
        if (snapshot.members().isEmpty() || snapshot.members().size() > MAX_PARTY_SIZE) return false;
        List<Character> online = parties.onlineMembers(anchor);
        return online.size() == snapshot.members().size()
                && online.stream().allMatch(member -> member.getMapId() == LOBBY_MAP_ID
                        && eligible(member, gateway, mode, progress));
    }

    private static List<Character> compatibleAgents(Character agent) {
        var clients = AgentClientGatewayRuntime.clients();
        if (agent == null) return List.of();
        if (!clients.hasClient(agent)) return List.of(agent);
        int world = clients.world(agent);
        int channel = clients.channel(agent);
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(candidate -> candidate != null && clients.hasClient(candidate)
                        && clients.world(candidate) == world && clients.channel(candidate) == channel
                        && candidate.getMapId() == LOBBY_MAP_ID)
                .sorted(Comparator.comparingInt(Character::getId))
                .toList();
    }

    private static List<Character> compatibleHumans(Character agent,
                                                    PartyGateway parties,
                                                    PrimitiveCapabilityGateway gateway,
                                                    Progress progress,
                                                    Mode mode) {
        if (agent == null || agent.getMap() == null) return List.of();
        var characters = server.agents.integration.AgentCharacterGatewayRuntime.characters();
        return agent.getMap().getAllPlayers().stream()
                .filter(candidate -> candidate != null && candidate != agent
                        && !characters.isHeadlessControlled(candidate)
                        && !parties.hasParty(candidate)
                        && eligible(candidate, gateway, mode, progress))
                .sorted(Comparator.comparingInt(Character::getId))
                .toList();
    }

    private static boolean eligible(Character candidate,
                                    PrimitiveCapabilityGateway gateway,
                                    Mode mode,
                                    Progress expectedProgress) {
        if (candidate == null) return false;
        if (mode == Mode.FARM) {
            return gateway.questStatus(candidate, 2336)
                    == QuestStatus.Status.COMPLETED.getId();
        }
        return gateway.questStatus(candidate, 2330) == QuestStatus.Status.STARTED.getId()
                && expectedProgress.equals(progress(candidate, gateway));
    }

    private static void cancelPendingHumanInvites(AgentMushroomKingdomYetiLobbyState state,
                                                  PartyGateway parties) {
        for (Integer inviteeId : state.yetiHumanInviteeIds()) {
            if (inviteeId != null) parties.cancelPartyInvite(inviteeId);
        }
        state.clearYetiHumanInvites();
    }

    private static int leaderId(AgentPartySnapshot party) {
        return party.members().stream()
                .filter(member -> member != null && member.leader())
                .mapToInt(member -> member.id())
                .findFirst().orElse(-1);
    }
}
