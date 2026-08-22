package server.agents.social.memory;

import server.agents.social.contracts.ConversationTurn;

import java.util.List;
import java.util.Optional;

/** Blocking persistence port. It may only be called from the persistence executor. */
public interface SocialMemoryStore {
    Optional<SocialRelationshipMemory> loadRelationship(SocialRelationshipKey key) throws Exception;

    List<ConversationTurn> loadRecentTurns(
            SocialRelationshipKey key, int limit, long nowMs) throws Exception;

    void saveRelationship(SocialRelationshipMemory memory) throws Exception;

    void appendTurn(
            SocialRelationshipKey key,
            String sessionId,
            ConversationTurn turn,
            int speakerId,
            long expiresAtMs) throws Exception;

    int deleteExpired(long nowMs) throws Exception;
}
