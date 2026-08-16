package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.social.PublicNegotiationSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class JdbcNegotiationEvidenceStore implements NegotiationEvidenceStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcNegotiationEvidenceStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void record(UUID runId, int itemId, Instant openedAt, Instant closedAt,
                       PublicNegotiationSession session, String settlementTransactionId) {
        String sql = "INSERT INTO negotiation_session (run_id, negotiation_id, buyer_id, seller_id, "
                + "item_id, opened_at, closed_at, status, transcript, settlement_transaction_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?) "
                + "ON CONFLICT (run_id, negotiation_id) DO UPDATE SET closed_at = EXCLUDED.closed_at, "
                + "status = EXCLUDED.status, transcript = EXCLUDED.transcript, "
                + "settlement_transaction_id = EXCLUDED.settlement_transaction_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, session.sessionId());
            statement.setString(3, session.initiator()); statement.setString(4, session.counterparty());
            statement.setInt(5, itemId); statement.setTimestamp(6, Timestamp.from(openedAt));
            statement.setTimestamp(7, Timestamp.from(closedAt));
            statement.setString(8, session.stateAt(closedAt).name());
            statement.setString(9, JSON.writeValueAsString(session.transcript()));
            statement.setString(10, settlementTransactionId);
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not persist negotiation evidence", failure);
        }
    }
}
