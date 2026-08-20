package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntent;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyIntentMatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentKpqLobbyProfileTest {
    @Test
    void everyAuthoredSubstringMapsToItsDeclaredIntent() {
        var profile = AgentKpqLobbyProfile.profile();

        profile.phrases().forEach(phrase -> assertEquals(
                phrase.intent(), AgentPartyQuestLobbyIntentMatcher.match(
                        profile, "hey, " + phrase.substring() + " please!"),
                phrase.substring()));
    }

    @Test
    void longerRecruitingPhraseWinsOverEmbeddedJoinPhrase() {
        assertEquals(AgentPartyQuestLobbyIntent.RECRUITING_MEMBERS,
                AgentPartyQuestLobbyIntentMatcher.match(
                        AgentKpqLobbyProfile.profile(), "Looking for KPQ members"));
    }

    @Test
    void normalizationHandlesCasePunctuationAndRepeatedSpacing() {
        assertEquals(AgentPartyQuestLobbyIntent.REQUEST_TO_JOIN,
                AgentPartyQuestLobbyIntentMatcher.match(
                        AgentKpqLobbyProfile.profile(), "LF...   KPQ!!!"));
    }

    @Test
    void unrelatedChatDoesNotTriggerLobbyBehavior() {
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(
                AgentKpqLobbyProfile.profile(), "looking for the Kerning taxi"));
        assertNull(AgentPartyQuestLobbyIntentMatcher.match(
                AgentKpqLobbyProfile.profile(), "can anyone help with a quest"));
    }
}
