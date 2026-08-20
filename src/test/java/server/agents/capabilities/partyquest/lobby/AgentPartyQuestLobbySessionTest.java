package server.agents.capabilities.partyquest.lobby;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyQuestLobbySessionTest {
    @Test
    void rosterChangeCancelsReadinessAndIncrementsRevision() {
        AgentPartyQuestLobbySession lobby = lobby();
        for (int id = 1; id <= 4; id++) {
            lobby.addMember(id, AgentPartyQuestLobbySession.MemberType.AGENT,
                    id == 1 ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                            : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_000L + id);
        }
        lobby.reconcileParty(50, 1, Set.of(1, 2, 3, 4), 1_100L);
        lobby.markReady(1_200L);
        long readyRevision = lobby.rosterRevision();

        lobby.reconcileParty(50, 1, Set.of(1, 2, 3), 1_300L);

        assertEquals(AgentPartyQuestLobbySession.State.FORMING, lobby.state());
        assertTrue(lobby.rosterRevision() > readyRevision);
        assertFalse(lobby.readyFor(Set.of(1, 2, 3)));
    }

    @Test
    void stableReadyRosterDoesNotFallBackToForming() {
        AgentPartyQuestLobbySession lobby = lobby();
        for (int id = 1; id <= 4; id++) {
            lobby.addMember(id, AgentPartyQuestLobbySession.MemberType.AGENT,
                    AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_000L + id);
        }
        lobby.reconcileParty(50, 1, Set.of(1, 2, 3, 4), 1_100L);
        lobby.markReady(1_200L);
        lobby.reconcileParty(50, 1, Set.of(1, 2, 3, 4), 1_300L);
        assertEquals(AgentPartyQuestLobbySession.State.READY, lobby.state());
    }

    @Test
    void addingOrRemovingMemberInvalidatesReadyRoster() {
        AgentPartyQuestLobbySession lobby = lobby();
        for (int id = 1; id <= 3; id++) {
            lobby.addMember(id, AgentPartyQuestLobbySession.MemberType.AGENT,
                    AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_000L + id);
        }
        lobby.reconcileParty(50, 1, Set.of(1, 2, 3), 1_100L);
        lobby.markReady(1_200L);

        lobby.addMember(4, AgentPartyQuestLobbySession.MemberType.HUMAN,
                AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_300L);
        assertEquals(AgentPartyQuestLobbySession.State.FORMING, lobby.state());

        lobby.markReady(1_400L);
        lobby.removeMember(4, 1_500L);
        assertEquals(AgentPartyQuestLobbySession.State.FORMING, lobby.state());
    }

    @Test
    void dynamicLobbyMemberPublicationDoesNotMutateOnConflict() {
        AgentPartyQuestLobbySession first = lobby();
        AgentPartyQuestLobbySession second = lobby();
        AgentPartyQuestLobbyRegistry.register(first);
        AgentPartyQuestLobbyRegistry.register(second);
        try {
            AgentPartyQuestLobbyRegistry.addAndIndexMember(
                    first, 10, AgentPartyQuestLobbySession.MemberType.HUMAN,
                    AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_100L);
            assertThrows(IllegalStateException.class,
                    () -> AgentPartyQuestLobbyRegistry.addAndIndexMember(
                            second, 10, AgentPartyQuestLobbySession.MemberType.HUMAN,
                            AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, 1_200L));
            assertFalse(second.contains(10));
        } finally {
            AgentPartyQuestLobbyRegistry.remove(first);
            AgentPartyQuestLobbyRegistry.remove(second);
        }
    }

    private static AgentPartyQuestLobbySession lobby() {
        return new AgentPartyQuestLobbySession(
                "engagement", new AgentPartyQuestLobbyProfile(
                "kpq", 1000, 9000, 21, 30, 4, -50, 50,
                List.of(), List.of(), List.of()),
                7L, 999, 4, AgentPartyQuestCandidateScope.OWNER_ONLY, 1_000L);
    }
}
