package server.agents.social.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import server.agents.social.contracts.ConversationTurn;
import server.agents.social.memory.SocialCounterpartyType;
import server.agents.social.memory.SocialRelationshipKey;
import server.agents.social.memory.SocialRelationshipMemory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "SOCIAL_DB_ENABLED", matches = "true")
class SocialPostgresSchemaIntegrationTest {
    @Test
    void schemaSupportsRelationshipAndBoundedTurnRoundTrip() throws Exception {
        try (var dataSource = SocialPostgresDataSource.fromEnvironment()) {
            new SocialDatabaseVerifier(dataSource).verify();
            JdbcSocialMemoryStore store = new JdbcSocialMemoryStore(dataSource);
            int suffix = Math.floorMod((int) System.nanoTime(), 100_000);
            SocialRelationshipKey key = new SocialRelationshipKey(
                    1_500_000 + suffix, SocialCounterpartyType.PLAYER, 1_700_000 + suffix);
            long nowMs = System.currentTimeMillis();
            SocialRelationshipMemory memory = SocialRelationshipMemory.neutral(key, nowMs)
                    .recordConversation(nowMs + 1);
            try {
                store.saveRelationship(memory);
                store.appendTurn(key, "integration", new ConversationTurn(
                        ConversationTurn.Role.HUMAN, "Alice", "hello", nowMs),
                        key.targetId(), nowMs + 60_000);

                assertEquals(memory, store.loadRelationship(key).orElseThrow());
                assertEquals("hello", store.loadRecentTurns(key, 8, nowMs).getFirst().text());
                assertTrue(store.deleteExpired(nowMs - 1) >= 0);
            } finally {
                try (var connection = dataSource.getConnection()) {
                    try (var statement = connection.prepareStatement(
                            "DELETE FROM agent_conversation_turn WHERE agent_id=? AND target_id=?")) {
                        statement.setInt(1, key.agentId());
                        statement.setInt(2, key.targetId());
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement(
                            "DELETE FROM agent_relationship_memory WHERE agent_id=? AND target_id=?")) {
                        statement.setInt(1, key.agentId());
                        statement.setInt(2, key.targetId());
                        statement.executeUpdate();
                    }
                }
            }
        }
    }
}
