package server.agents.social.memory;

import server.agents.social.contracts.ConversationTurn;

import java.util.List;

public record SocialMemorySnapshot(
        SocialRelationshipMemory relationship,
        List<ConversationTurn> recentTurns) {
    public SocialMemorySnapshot {
        if (relationship == null) {
            throw new IllegalArgumentException("relationship memory is required");
        }
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }
}
