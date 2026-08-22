package server.agents.social.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.social.contracts.ConversationTurn;
import server.agents.social.persistence.SocialMemoryDatabaseRuntime;
import server.agents.social.persistence.SocialPostgresDataSource;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.async.AgentAsyncWorkKind;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Non-blocking social-memory facade. Hot paths only touch the bounded cache;
 * all independent-database access runs on the persistence executor.
 */
public final class SocialMemoryPersistenceRuntime {
    private static final Logger log = LoggerFactory.getLogger(SocialMemoryPersistenceRuntime.class);
    private static final long TURN_RETENTION_MS = Duration.ofDays(7).toMillis();
    private static final SocialMemoryCache CACHE = new SocialMemoryCache();
    private static final Set<SocialRelationshipKey> HYDRATING = ConcurrentHashMap.newKeySet();
    private static final Set<SocialRelationshipKey> HYDRATED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong writes = new AtomicLong();

    private SocialMemoryPersistenceRuntime() {
    }

    public static SocialMemorySnapshot snapshot(SocialRelationshipKey key, long nowMs) {
        ensureHydrated(key, nowMs);
        return CACHE.snapshot(key, nowMs);
    }

    public static void recordConversation(
            SocialRelationshipKey key,
            String sessionId,
            int playerId,
            ConversationTurn playerTurn,
            ConversationTurn agentTurn,
            long nowMs) {
        SocialRelationshipMemory relationship = CACHE.recordConversation(
                key, playerTurn, agentTurn, nowMs);
        submit(() -> persist(key, sessionId, playerId, playerTurn, agentTurn, relationship, nowMs));
    }

    public static void clearAgentRuntimeState(int agentId) {
        CACHE.clearAgent(agentId);
        HYDRATING.removeIf(key -> key.agentId() == agentId);
        HYDRATED.removeIf(key -> key.agentId() == agentId);
    }

    static void resetForTests() {
        HYDRATING.clear();
        HYDRATED.clear();
    }

    private static void ensureHydrated(SocialRelationshipKey key, long nowMs) {
        if (HYDRATED.contains(key) || !HYDRATING.add(key)) {
            return;
        }
        if (!submit(() -> hydrate(key, nowMs))) {
            HYDRATING.remove(key);
        }
    }

    private static void hydrate(SocialRelationshipKey key, long nowMs) {
        try {
            Optional<SocialMemoryStore> store = SocialMemoryDatabaseRuntime.store();
            if (store.isPresent()) {
                CACHE.hydrate(
                        key,
                        store.get().loadRelationship(key).orElse(null),
                        store.get().loadRecentTurns(key, SocialMemoryCache.MAX_RECENT_TURNS, nowMs));
                HYDRATED.add(key);
            } else if (!SocialPostgresDataSource.enabled()) {
                HYDRATED.add(key);
            }
        } catch (Exception failure) {
            log.warn("Could not hydrate social memory for agent {} target {}: {}",
                    key.agentId(), key.targetId(), failure.toString());
        } finally {
            HYDRATING.remove(key);
        }
    }

    private static void persist(
            SocialRelationshipKey key,
            String sessionId,
            int playerId,
            ConversationTurn playerTurn,
            ConversationTurn agentTurn,
            SocialRelationshipMemory relationship,
            long nowMs) {
        try {
            Optional<SocialMemoryStore> store = SocialMemoryDatabaseRuntime.store();
            if (store.isEmpty()) {
                return;
            }
            long expiresAtMs = nowMs + TURN_RETENTION_MS;
            store.get().saveRelationship(relationship);
            store.get().appendTurn(key, sessionId, playerTurn, playerId, expiresAtMs);
            store.get().appendTurn(key, sessionId, agentTurn, key.agentId(), expiresAtMs);
            if ((writes.incrementAndGet() & 1023L) == 0L) {
                store.get().deleteExpired(nowMs);
            }
        } catch (Exception failure) {
            log.warn("Could not persist social memory for agent {} target {}: {}",
                    key.agentId(), key.targetId(), failure.toString());
        }
    }

    private static boolean submit(Runnable task) {
        try {
            AgentAsyncExecutorRegistry.runtime().execute(AgentAsyncWorkKind.PERSISTENCE, task);
            return true;
        } catch (RejectedExecutionException failure) {
            log.debug("Social persistence deferred because the queue is unavailable");
            return false;
        }
    }
}
