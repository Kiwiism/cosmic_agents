package server.agents.social.memory;

import server.agents.social.contracts.ConversationTurn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Non-blocking bounded read model used by Agent mailboxes and prompt projection. */
public final class SocialMemoryCache {
    public static final int MAX_RECENT_TURNS = 8;

    private final ConcurrentMap<SocialRelationshipKey, SocialRelationshipMemory> relationships =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<SocialRelationshipKey, ArrayDeque<ConversationTurn>> turns =
            new ConcurrentHashMap<>();

    public SocialMemorySnapshot snapshot(SocialRelationshipKey key, long nowMs) {
        SocialRelationshipMemory relationship = relationships.getOrDefault(
                key, SocialRelationshipMemory.neutral(key, nowMs));
        ArrayDeque<ConversationTurn> history = turns.get(key);
        if (history == null) {
            return new SocialMemorySnapshot(relationship, List.of());
        }
        synchronized (history) {
            return new SocialMemorySnapshot(relationship, new ArrayList<>(history));
        }
    }

    public SocialRelationshipMemory recordConversation(
            SocialRelationshipKey key,
            ConversationTurn playerTurn,
            ConversationTurn agentTurn,
            long nowMs) {
        SocialRelationshipMemory relationship = relationships.compute(
                key,
                (ignored, current) -> (current == null
                        ? SocialRelationshipMemory.neutral(key, nowMs)
                        : current).recordConversation(nowMs));
        append(key, playerTurn);
        append(key, agentTurn);
        return relationship;
    }

    public void hydrate(
            SocialRelationshipKey key,
            SocialRelationshipMemory relationship,
            List<ConversationTurn> recentTurns) {
        if (relationship != null) {
            relationships.merge(key, relationship,
                    SocialMemoryCache::mergeRelationship);
        }
        if (recentTurns == null || recentTurns.isEmpty()) {
            return;
        }
        ArrayDeque<ConversationTurn> history = turns.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (history) {
            List<ConversationTurn> merged = new ArrayList<>(recentTurns);
            for (ConversationTurn current : history) {
                if (!merged.contains(current)) {
                    merged.add(current);
                }
            }
            merged.sort(java.util.Comparator.comparingLong(ConversationTurn::occurredAtMs));
            history.clear();
            merged.stream()
                    .skip(Math.max(0, merged.size() - MAX_RECENT_TURNS))
                    .forEach(history::addLast);
        }
    }

    private static SocialRelationshipMemory mergeRelationship(
            SocialRelationshipMemory current,
            SocialRelationshipMemory loaded) {
        if (current.interactionCount() > 0
                && current.createdAtMs() >= loaded.lastInteractionAtMs()) {
            SocialRelationshipMemory merged = loaded;
            for (long index = 0; index < current.interactionCount(); index++) {
                merged = merged.recordConversation(current.lastInteractionAtMs());
            }
            return merged;
        }
        return current.revision() >= loaded.revision() ? current : loaded;
    }

    public void clearAgent(int agentId) {
        relationships.keySet().removeIf(key -> key.agentId() == agentId);
        turns.keySet().removeIf(key -> key.agentId() == agentId);
    }

    private void append(SocialRelationshipKey key, ConversationTurn turn) {
        ArrayDeque<ConversationTurn> history = turns.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (history) {
            while (history.size() >= MAX_RECENT_TURNS) {
                history.removeFirst();
            }
            history.addLast(turn);
        }
    }
}
