package server.agents.capabilities.partyquest.lobby;

import client.Character;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.TimerManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPartyQuestLobbyRuntimeTest {
    private static final int AGENT_ID = 101;
    private static final int OWNER_ID = 201;
    private AgentPartyQuestLobbySession lobby;

    @BeforeAll
    static void startTimer() {
        TimerManager.getInstance().start();
    }

    @AfterAll
    static void stopTimer() {
        TimerManager.getInstance().stop();
    }

    @AfterEach
    void unregisterLobby() {
        if (lobby != null) AgentPartyQuestLobbyRuntime.unregister(lobby.lobbyId());
    }

    @Test
    void ordinaryAgentInvitesAreNotRestricted() {
        Character agent = character(AGENT_ID, 25, 1000);

        assertEquals(AgentPartyQuestLobbyRuntime.InviteDecision.NOT_LOBBY_WAITER,
                AgentPartyQuestLobbyRuntime.decidePartyInvite(agent, null));
    }

    @Test
    void registeredWaiterRejectsAnotherHuman() {
        registerWaiter();
        Character agent = character(AGENT_ID, 25, 1000);
        Character stranger = character(OWNER_ID + 1, 25, 1000);

        assertEquals(AgentPartyQuestLobbyRuntime.InviteDecision.REJECT,
                AgentPartyQuestLobbyRuntime.decidePartyInvite(agent, stranger));
    }

    @Test
    void registeredWaiterRejectsOwnerOutsideProfileLevelRange() {
        registerWaiter();
        Character agent = character(AGENT_ID, 25, 1000);
        Character owner = character(OWNER_ID, 31, 1000);

        assertEquals(AgentPartyQuestLobbyRuntime.InviteDecision.REJECT,
                AgentPartyQuestLobbyRuntime.decidePartyInvite(agent, owner));
    }

    @Test
    void agentLeaderInviteResponseUsesConfiguredHumanDelayBounds() {
        long delay = AgentPartyQuestLobbyRuntime.inviteResponseDelayMs(
                17L, AGENT_ID, OWNER_ID, 900L, 1_800L);

        assertTrue(delay >= 900L);
        assertTrue(delay <= 1_800L);
        assertEquals(delay, AgentPartyQuestLobbyRuntime.inviteResponseDelayMs(
                17L, AGENT_ID, OWNER_ID, 900L, 1_800L));
    }

    @Test
    void profileMayOverrideOnlyItsOwnInviteResponseBounds() {
        AgentPartyQuestLobbyProfile profile = new AgentPartyQuestLobbyProfile(
                "hpq", 1000, 9000, 10, 255, 6, -50, 50,
                List.of(), List.of(), List.of(), 2_000L, 5_000L);

        assertEquals(2_000L, profile.inviteResponseMinimumMs());
        assertEquals(5_000L, profile.inviteResponseMaximumMs());
        long delay = AgentPartyQuestLobbyRuntime.inviteResponseDelayMs(
                17L, AGENT_ID, OWNER_ID,
                profile.inviteResponseMinimumMs(), profile.inviteResponseMaximumMs());
        assertTrue(delay >= 2_000L && delay <= 5_000L);
    }

    private void registerWaiter() {
        lobby = new AgentPartyQuestLobbySession(
                "engagement-test", profile(), 1L, OWNER_ID, 4,
                AgentPartyQuestCandidateScope.OWNER_ONLY, 1_000L);
        lobby.addMember(AGENT_ID, AgentPartyQuestLobbySession.MemberType.AGENT,
                AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY, 1_000L);
        AgentPartyQuestLobbyRuntime.register(lobby, 1_000L);
    }

    private static AgentPartyQuestLobbyProfile profile() {
        return new AgentPartyQuestLobbyProfile(
                "testpq", 1000, 9000, 21, 30, 4, -50, 50,
                List.of(), List.of(), List.of());
    }

    private static Character character(int id, int level, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getLevel()).thenReturn(level);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }
}
