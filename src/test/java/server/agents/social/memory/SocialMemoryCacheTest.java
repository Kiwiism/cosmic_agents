package server.agents.social.memory;

import org.junit.jupiter.api.Test;
import server.agents.social.contracts.ConversationTurn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialMemoryCacheTest {
    @Test
    void recordsDirectionalRelationshipWithoutStoringRawChatInSummary() {
        SocialMemoryCache cache = new SocialMemoryCache();
        SocialRelationshipKey key = new SocialRelationshipKey(100, SocialCounterpartyType.PLAYER, 200);

        SocialRelationshipMemory memory = cache.recordConversation(
                key,
                new ConversationTurn(ConversationTurn.Role.HUMAN, "Alice", "my private phrase", 10),
                new ConversationTurn(ConversationTurn.Role.AGENT, "Mina", "hey", 11),
                11);

        assertEquals(1, memory.interactionCount());
        assertTrue(memory.familiarity() > 0.0);
        assertTrue(!memory.summary().contains("private phrase"));
        assertEquals(2, cache.snapshot(key, 12).recentTurns().size());
    }

    @Test
    void recentTurnsRemainBounded() {
        SocialMemoryCache cache = new SocialMemoryCache();
        SocialRelationshipKey key = new SocialRelationshipKey(101, SocialCounterpartyType.PLAYER, 201);
        for (int index = 0; index < 10; index++) {
            cache.recordConversation(
                    key,
                    new ConversationTurn(ConversationTurn.Role.HUMAN, "Alice", "m" + index, index * 2L),
                    new ConversationTurn(ConversationTurn.Role.AGENT, "Mina", "r" + index, index * 2L + 1),
                    index * 2L + 1);
        }

        assertEquals(SocialMemoryCache.MAX_RECENT_TURNS, cache.snapshot(key, 30).recentTurns().size());
    }
}
