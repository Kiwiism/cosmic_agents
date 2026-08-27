package server.agents.progression;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPartyMemberSnapshot;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.PartyGateway;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMushroomKingdomYetiPartyRuntimeTest {
    @Test
    void formsAThreeAgentPartyAndOnlyTheLeaderStartsTheInstance() {
        Character first = character(1);
        Character second = character(2);
        Character third = character(3);
        List<Character> compatible = List.of(first, second, third);
        PrimitiveCapabilityGateway gateway = matchingProgressGateway(compatible);
        PartyHarness parties = new PartyHarness();
        AgentMushroomKingdomState secondState = state();

        var memberDecision = AgentMushroomKingdomYetiPartyRuntime.prepare(
                second, secondState, gateway, parties.gateway, compatible,
                List.of(),
                new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 1, 0),
                1_000L, 5_000L, 7_000L);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING, memberDecision);
        assertEquals(List.of(first, second, third), parties.membersByCharacter.get(first));

        var leaderDecision = AgentMushroomKingdomYetiPartyRuntime.prepare(
                first, state(), gateway, parties.gateway, compatible,
                List.of(),
                new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 1, 0),
                1_001L, 5_000L, 7_000L);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.READY_LEADER, leaderDecision);
    }

    @Test
    void progressComparisonIncludesEachYetiColorSeparately() {
        Character first = character(1);
        Character second = character(2);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questProgress(first, 2330, 3300005)).thenReturn(1);
        when(gateway.questProgress(second, 2330, 3300006)).thenReturn(1);

        assertNotEquals(
                AgentMushroomKingdomYetiPartyRuntime.progress(first, gateway),
                AgentMushroomKingdomYetiPartyRuntime.progress(second, gateway));
    }

    @Test
    void scansAgentsForFiveSecondsThenCancelsAnUnansweredHumanInviteAfterSevenSeconds() {
        Character agent = character(1);
        Character human = character(20);
        List<Character> compatibleAgents = List.of(agent);
        PrimitiveCapabilityGateway gateway = matchingProgressGateway(List.of(agent, human));
        PartyHarness parties = new PartyHarness();
        AgentMushroomKingdomState state = state();
        var progress = new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 1, 0);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                        progress, 1_000L, 5_000L, 7_000L));
        assertEquals(List.of(), parties.invitedIds);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                        progress, 6_000L, 5_000L, 7_000L));
        assertEquals(List.of(20), parties.invitedIds);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                        progress, 12_999L, 5_000L, 7_000L));
        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.READY_LEADER,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                        progress, 13_000L, 5_000L, 7_000L));
        assertEquals(Set.of(), parties.pendingInviteIds);
        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.READY_LEADER,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                        progress, 14_000L, 5_000L, 7_000L));
        assertEquals(List.of(20), parties.invitedIds);
    }

    @Test
    void proceedsImmediatelyWhenTheInvitedHumanAccepts() {
        Character agent = character(1);
        Character human = character(20);
        List<Character> compatibleAgents = List.of(agent);
        PrimitiveCapabilityGateway gateway = matchingProgressGateway(List.of(agent, human));
        PartyHarness parties = new PartyHarness();
        AgentMushroomKingdomState state = state();
        var progress = new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 1, 0);

        AgentMushroomKingdomYetiPartyRuntime.prepare(
                agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                progress, 1_000L, 5_000L, 7_000L);
        AgentMushroomKingdomYetiPartyRuntime.prepare(
                agent, state, gateway, parties.gateway, compatibleAgents, List.of(human),
                progress, 6_000L, 5_000L, 7_000L);
        parties.pendingInviteIds.remove(human.getId());
        parties.assign(List.of(agent, human));

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.READY_LEADER,
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, parties.gateway, compatibleAgents, List.of(),
                        progress, 6_001L, 5_000L, 7_000L));
    }

    @Test
    void farmModeMatchesCompletedPlayersWithoutComparingStoryColorProgress() {
        Character first = character(1);
        Character second = character(2);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.questStatus(first, 2336))
                .thenReturn(QuestStatus.Status.COMPLETED.getId());
        when(gateway.questStatus(second, 2336))
                .thenReturn(QuestStatus.Status.COMPLETED.getId());
        when(gateway.questProgress(first, 2330, 3300005)).thenReturn(1);
        when(gateway.questProgress(second, 2330, 3300007)).thenReturn(1);
        PartyHarness parties = new PartyHarness();

        var waiting = AgentMushroomKingdomYetiPartyRuntime.prepare(
                first, new AgentMushroomKingdomPostStoryState(), gateway,
                parties.gateway, List.of(first, second), List.of(),
                new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 0, 0),
                AgentMushroomKingdomYetiPartyRuntime.Mode.FARM,
                6_000L, 5_000L, 7_000L);
        AgentMushroomKingdomPostStoryState leaderState = new AgentMushroomKingdomPostStoryState();
        AgentMushroomKingdomYetiPartyRuntime.prepare(
                first, leaderState, gateway, parties.gateway, List.of(first, second), List.of(),
                new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 0, 0),
                AgentMushroomKingdomYetiPartyRuntime.Mode.FARM,
                6_000L, 5_000L, 7_000L);
        var decision = AgentMushroomKingdomYetiPartyRuntime.prepare(
                first, leaderState, gateway, parties.gateway, List.of(first, second), List.of(),
                new AgentMushroomKingdomYetiPartyRuntime.Progress(0, 0, 0),
                AgentMushroomKingdomYetiPartyRuntime.Mode.FARM,
                11_000L, 5_000L, 7_000L);

        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING, waiting);
        assertEquals(AgentMushroomKingdomYetiPartyRuntime.Decision.READY_LEADER, decision);
        assertEquals(List.of(first, second), parties.membersByCharacter.get(first));
    }

    private static AgentMushroomKingdomState state() {
        AgentMushroomKingdomState state = new AgentMushroomKingdomState();
        state.begin(1L);
        return state;
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(AgentMushroomKingdomYetiPartyRuntime.LOBBY_MAP_ID);
        return character;
    }

    private static PrimitiveCapabilityGateway matchingProgressGateway(List<Character> agents) {
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        for (Character agent : agents) {
            when(gateway.questStatus(agent, 2330))
                    .thenReturn(QuestStatus.Status.STARTED.getId());
            when(gateway.questProgress(agent, 2330, 3300006)).thenReturn(1);
        }
        return gateway;
    }

    private static final class PartyHarness {
        private static final int PARTY_ID = 42;
        private final PartyGateway gateway = mock(PartyGateway.class);
        private final Map<Character, List<Character>> membersByCharacter = new HashMap<>();
        private final List<Integer> invitedIds = new ArrayList<>();
        private final Set<Integer> pendingInviteIds = new HashSet<>();

        private PartyHarness() {
            when(gateway.snapshot(org.mockito.ArgumentMatchers.any(Character.class)))
                    .thenAnswer(invocation -> snapshot(invocation.getArgument(0)));
            when(gateway.onlineMembers(org.mockito.ArgumentMatchers.any(Character.class)))
                    .thenAnswer(invocation -> membersByCharacter.getOrDefault(
                            invocation.getArgument(0), List.of()));
            when(gateway.createAgentParty(org.mockito.ArgumentMatchers.any(Character.class)))
                    .thenAnswer(invocation -> {
                        Character leader = invocation.getArgument(0);
                        assign(List.of(leader));
                        return true;
                    });
            when(gateway.joinAgentParty(
                    org.mockito.ArgumentMatchers.any(Character.class), anyInt()))
                    .thenAnswer(invocation -> {
                        Character member = invocation.getArgument(0);
                        List<Character> current = new ArrayList<>(membersByCharacter.values()
                                .stream().findFirst().orElse(List.of()));
                        current.add(member);
                        assign(List.copyOf(current));
                        return true;
                    });
            when(gateway.hasParty(org.mockito.ArgumentMatchers.any(Character.class)))
                    .thenAnswer(invocation -> membersByCharacter.containsKey(invocation.getArgument(0)));
            when(gateway.invitePartyMember(
                    org.mockito.ArgumentMatchers.any(Character.class),
                    org.mockito.ArgumentMatchers.any(Character.class)))
                    .thenAnswer(invocation -> {
                        Character invitee = invocation.getArgument(1);
                        invitedIds.add(invitee.getId());
                        pendingInviteIds.add(invitee.getId());
                        return true;
                    });
            when(gateway.hasPendingPartyInvite(anyInt()))
                    .thenAnswer(invocation -> pendingInviteIds.contains(invocation.getArgument(0)));
            doAnswer(invocation -> {
                pendingInviteIds.remove((Integer) invocation.getArgument(0));
                return null;
            }).when(gateway).cancelPartyInvite(anyInt());
            doAnswer(invocation -> {
                Character member = invocation.getArgument(0);
                membersByCharacter.remove(member);
                return null;
            }).when(gateway).leaveCurrentParty(org.mockito.ArgumentMatchers.any(Character.class));
        }

        private AgentPartySnapshot snapshot(Character character) {
            List<Character> members = membersByCharacter.get(character);
            if (members == null) return null;
            int leaderId = members.getFirst().getId();
            return new AgentPartySnapshot(PARTY_ID, members.stream()
                    .map(member -> new AgentPartyMemberSnapshot(
                            member.getId(), "agent-" + member.getId(),
                            member.getId() == leaderId, member.getMapId()))
                    .toList());
        }

        private void assign(List<Character> members) {
            members.forEach(member -> membersByCharacter.put(member, members));
        }
    }
}
