package server.agents.capabilities.partyquest.epq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEpqLobbyProfileTest {
    @Test
    void configuresTheSharedLobbyForEpqOnly() {
        var profile = AgentEpqLobbyProfile.profile();
        assertEquals("epq", profile.questKey());
        assertEquals(AgentEpqDefinition.RECRUIT_MAP, profile.mapId());
        assertEquals(AgentEpqDefinition.ENTRY_NPC, profile.entryNpcId());
        assertEquals(44, profile.minimumLevel());
        assertEquals(55, profile.maximumLevel());
        assertEquals(6, profile.maximumPartySize());
        assertTrue(profile.phrases().stream().anyMatch(phrase ->
                phrase.intent() == AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS
                        && phrase.substring().contains("ellin forest pq")));
    }
}
