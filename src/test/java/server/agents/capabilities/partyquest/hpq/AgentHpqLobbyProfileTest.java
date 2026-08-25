package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntentMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentHpqLobbyProfileTest {
    @Test
    void usesHpqVocabularyInTheStandardLobbyMatcher() {
        var profile = AgentHpqLobbyProfile.profile();
        assertEquals("hpq", profile.questKey());
        assertEquals(AgentHpqDefinition.RECRUIT_MAP, profile.mapId());
        profile.phrases().forEach(phrase -> assertEquals(
                phrase.intent(), AgentPartyQuestLobbyIntentMatcher.match(
                        profile, "hey, " + phrase.substring() + " please"),
                phrase.substring()));
        assertEquals(AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                AgentPartyQuestLobbyIntentMatcher.match(profile, "HPQ recruiting"));
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(profile, "KPQ recruiting"));
    }
}
