package server.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
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

    private static String evidenceJson(SecurityEvent event) throws JsonProcessingException {
        return json.writeValueAsString(event.evidence());
    }
}
