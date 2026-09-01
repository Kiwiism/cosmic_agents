package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntentMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentLpqLobbyProfileTest {
    @Test
    void recognizesCommonLpqJoinRequestsWithTheStandardLobbyDelay() {
        var profile = AgentLpqLobbyProfile.profile();
        for (String request : new String[]{
                "looking for lpq", "looking for pq", "joining pq", "lf ludi pq", "join Ludibrium PQ",
                "invite me LPQ", "looking to join tower pq"}) {
            assertEquals(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                    AgentPartyQuestLobbyIntentMatcher.match(profile, request), request);
        }
        assertEquals(2_000L, profile.inviteResponseMinimumMs());
        assertEquals(2_400L, profile.inviteResponseMaximumMs());
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(profile, "looking for kpq"));
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(profile, "join hpq"));
    }
}
