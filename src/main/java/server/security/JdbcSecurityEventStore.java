package server.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class JdbcSecurityEventStore implements SecurityEventStore {
    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public void append(SecurityEvent event) {
        String sql = "INSERT INTO security_event "
                + "(event_id, occurred_at, event_type, severity, account_id, character_id, "
                + "remote_fingerprint, evidence_json, review_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.eventId().toString());
            statement.setTimestamp(2, Timestamp.from(event.occurredAt()));
            statement.setString(3, event.type().name());
            statement.setString(4, event.severity().name());
            statement.setInt(5, event.accountId());
            statement.setInt(6, event.characterId());
            statement.setString(7, event.remoteFingerprint());
            statement.setString(8, evidenceJson(event));
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException e) {
            throw new SecurityEventStoreException("Could not persist security event", e);
        }
    }

    @Override
    public boolean markReviewed(UUID eventId, String reviewer, String note) {
        String sql = "UPDATE security_event SET review_status = 'REVIEWED', reviewed_by = ?, "
                + "review_note = ?, reviewed_at = CURRENT_TIMESTAMP "
                + "WHERE event_id = ? AND review_status = 'OPEN'";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reviewer);
            statement.setString(2, note);
            statement.setString(3, eventId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new SecurityEventStoreException("Could not review security event", e);
        }
    }

    @Override
    public List<SecurityEventReviewRecord> findOpen(int limit) {
        String sql = "SELECT event_id, occurred_at, event_type, severity, account_id, character_id, "
                + "remote_fingerprint, evidence_json FROM security_event WHERE review_status = 'OPEN' "
                + "ORDER BY occurred_at DESC LIMIT ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                ArrayList<SecurityEventReviewRecord> events = new ArrayList<>();
                while (result.next()) {
                    events.add(new SecurityEventReviewRecord(
                            UUID.fromString(result.getString("event_id")),
                            result.getTimestamp("occurred_at").toInstant(),
                            SecurityEventType.valueOf(result.getString("event_type")),
                            SecuritySeverity.valueOf(result.getString("severity")),
                            result.getInt("account_id"), result.getInt("character_id"),
                            result.getString("remote_fingerprint"),
                            json.readValue(result.getString("evidence_json"), new TypeReference<Map<String, String>>() { })));
                }
                return List.copyOf(events);
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new SecurityEventStoreException("Could not query open security events", e);
        }
    }

    @Override
    public int deleteReviewedBefore(Instant cutoff) {
        String sql = "DELETE FROM security_event WHERE review_status = 'REVIEWED' AND reviewed_at < ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new SecurityEventStoreException("Could not apply security-event retention", e);
        }
    }

    private static String evidenceJson(SecurityEvent event) throws JsonProcessingException {
        return json.writeValueAsString(event.evidence());
    }
}
