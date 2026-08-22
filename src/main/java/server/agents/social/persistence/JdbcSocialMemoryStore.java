package server.agents.social.persistence;

import server.agents.social.contracts.ConversationTurn;
import server.agents.social.memory.SocialCounterpartyType;
import server.agents.social.memory.SocialMemoryStore;
import server.agents.social.memory.SocialRelationshipKey;
import server.agents.social.memory.SocialRelationshipMemory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Blocking JDBC adapter for the dedicated social-memory database. */
public final class JdbcSocialMemoryStore implements SocialMemoryStore {
    private final DataSource dataSource;

    public JdbcSocialMemoryStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public Optional<SocialRelationshipMemory> loadRelationship(SocialRelationshipKey key) throws Exception {
        String sql = "SELECT familiarity, trust, affinity, annoyance, interaction_count, summary, "
                + "created_at_ms, last_interaction_at_ms, revision FROM agent_relationship_memory "
                + "WHERE agent_id=? AND target_type=? AND target_id=?";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new SocialRelationshipMemory(
                        key,
                        result.getDouble("familiarity"),
                        result.getDouble("trust"),
                        result.getDouble("affinity"),
                        result.getDouble("annoyance"),
                        result.getLong("interaction_count"),
                        result.getString("summary"),
                        result.getLong("created_at_ms"),
                        result.getLong("last_interaction_at_ms"),
                        result.getLong("revision")));
            }
        }
    }

    @Override
    public List<ConversationTurn> loadRecentTurns(
            SocialRelationshipKey key, int limit, long nowMs) throws Exception {
        String sql = "SELECT role, speaker_name, text, occurred_at_ms FROM agent_conversation_turn "
                + "WHERE agent_id=? AND target_type=? AND target_id=? AND expires_at_ms>? "
                + "ORDER BY occurred_at_ms DESC, turn_id DESC LIMIT ?";
        List<ConversationTurn> reversed = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key);
            statement.setLong(4, nowMs);
            statement.setInt(5, Math.max(1, Math.min(limit, 32)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    reversed.add(new ConversationTurn(
                            ConversationTurn.Role.valueOf(result.getString("role")),
                            result.getString("speaker_name"),
                            result.getString("text"),
                            result.getLong("occurred_at_ms")));
                }
            }
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    @Override
    public void saveRelationship(SocialRelationshipMemory memory) throws Exception {
        String sql = "INSERT INTO agent_relationship_memory "
                + "(agent_id,target_type,target_id,familiarity,trust,affinity,annoyance,interaction_count,"
                + "summary,created_at_ms,last_interaction_at_ms,revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (agent_id,target_type,target_id) DO UPDATE SET "
                + "familiarity=EXCLUDED.familiarity,trust=EXCLUDED.trust,affinity=EXCLUDED.affinity,"
                + "annoyance=EXCLUDED.annoyance,interaction_count=EXCLUDED.interaction_count,"
                + "summary=EXCLUDED.summary,last_interaction_at_ms=EXCLUDED.last_interaction_at_ms,"
                + "revision=EXCLUDED.revision WHERE agent_relationship_memory.revision < EXCLUDED.revision";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, memory.key());
            statement.setDouble(4, memory.familiarity());
            statement.setDouble(5, memory.trust());
            statement.setDouble(6, memory.affinity());
            statement.setDouble(7, memory.annoyance());
            statement.setLong(8, memory.interactionCount());
            statement.setString(9, memory.summary());
            statement.setLong(10, memory.createdAtMs());
            statement.setLong(11, memory.lastInteractionAtMs());
            statement.setLong(12, memory.revision());
            statement.executeUpdate();
        }
    }

    @Override
    public void appendTurn(
            SocialRelationshipKey key,
            String sessionId,
            ConversationTurn turn,
            int speakerId,
            long expiresAtMs) throws Exception {
        String sql = "INSERT INTO agent_conversation_turn "
                + "(agent_id,target_type,target_id,session_id,role,speaker_id,speaker_name,text,"
                + "occurred_at_ms,expires_at_ms) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, key);
            statement.setString(4, sessionId);
            statement.setString(5, turn.role().name());
            statement.setInt(6, speakerId);
            statement.setString(7, turn.speakerName());
            statement.setString(8, turn.text());
            statement.setLong(9, turn.occurredAtMs());
            statement.setLong(10, expiresAtMs);
            statement.executeUpdate();
        }
    }

    @Override
    public int deleteExpired(long nowMs) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM agent_conversation_turn WHERE expires_at_ms<=?")) {
            statement.setLong(1, nowMs);
            return statement.executeUpdate();
        }
    }

    private static void bindKey(PreparedStatement statement, SocialRelationshipKey key) throws Exception {
        statement.setInt(1, key.agentId());
        statement.setString(2, key.targetType().name());
        statement.setInt(3, key.targetId());
    }
}
