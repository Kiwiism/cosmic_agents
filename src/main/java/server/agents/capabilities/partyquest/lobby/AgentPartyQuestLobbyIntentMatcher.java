package server.agents.capabilities.partyquest.lobby;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;

/** Normalized, word-boundary substring matching for party-quest lobby chat. */
public final class AgentPartyQuestLobbyIntentMatcher {
    private AgentPartyQuestLobbyIntentMatcher() {
    }

    public static AgentPartyQuestLobbyIntent match(
            AgentPartyQuestLobbyProfile profile, String message) {
        if (profile == null || message == null || message.isBlank()) return null;
        String normalized = " " + normalize(message) + " ";
        return profile.phrases().stream()
                .sorted(Comparator.comparingInt(
                        (AgentPartyQuestLobbyProfile.Phrase phrase) -> normalize(phrase.substring()).length())
                        .reversed())
                .filter(phrase -> normalized.contains(" " + normalize(phrase.substring()) + " "))
                .map(AgentPartyQuestLobbyProfile.Phrase::intent)
                .findFirst().orElse(null);
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT);
        return decomposed.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
