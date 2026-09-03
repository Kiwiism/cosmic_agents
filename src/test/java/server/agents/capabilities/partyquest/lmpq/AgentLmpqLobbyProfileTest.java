package server.agents.capabilities.partyquest.lmpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntentMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentLmpqLobbyProfileTest {
    @Test
    void recognizesMazeRequestsWithoutClaimingOrdinaryLpqChat() {
        var profile = AgentLmpqLobbyProfile.profile();
        assertEquals(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                AgentPartyQuestLobbyIntentMatcher.match(profile, "looking for lmpq"));
        assertEquals(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                AgentPartyQuestLobbyIntentMatcher.match(profile, "invite me Ludibrium Maze PQ"));
        assertEquals(AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                AgentPartyQuestLobbyIntentMatcher.match(profile, "lfm ludi maze"));
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(profile, "looking for lpq"));
    }
}
