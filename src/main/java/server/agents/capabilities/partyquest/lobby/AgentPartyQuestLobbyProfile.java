package server.agents.capabilities.partyquest.lobby;

import java.util.List;
import java.util.function.Predicate;

import client.Character;

/** Party-quest-specific data consumed by the reusable lobby runtime. */
public record AgentPartyQuestLobbyProfile(
        String questKey,
        int mapId,
        int entryNpcId,
        int minimumLevel,
        int maximumLevel,
        int maximumPartySize,
        int minimumXOffset,
        int maximumXOffset,
        List<Phrase> phrases,
        List<MemberRequirement> memberRequirements,
        List<String> waiterMessages) {

    public AgentPartyQuestLobbyProfile {
        if (questKey == null || questKey.isBlank() || minimumLevel < 1
                || maximumLevel < minimumLevel || maximumPartySize < 2
                || minimumXOffset > maximumXOffset) {
            throw new IllegalArgumentException("Valid party-quest lobby profile values are required");
        }
        phrases = List.copyOf(phrases == null ? List.of() : phrases);
        memberRequirements = List.copyOf(memberRequirements == null ? List.of() : memberRequirements);
        waiterMessages = List.copyOf(waiterMessages == null ? List.of() : waiterMessages);
    }

    public record Phrase(AgentPartyQuestLobbyIntent intent, String substring) {
        public Phrase {
            if (intent == null || substring == null || substring.isBlank()) {
                throw new IllegalArgumentException("Lobby phrase intent and substring are required");
            }
        }
    }

    /** Activity-specific roster need, for example an LPQ thief with Dark Sight. */
    public record MemberRequirement(
            String description, int minimumCount, Predicate<Character> matcher) {
        public MemberRequirement {
            if (description == null || description.isBlank() || minimumCount < 1 || matcher == null) {
                throw new IllegalArgumentException("Lobby member requirement values are required");
            }
        }

        public boolean matches(Character character) {
            return character != null && matcher.test(character);
        }
    }
}
