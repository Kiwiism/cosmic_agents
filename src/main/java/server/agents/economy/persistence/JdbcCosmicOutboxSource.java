package server.agents.economy.persistence;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcCosmicOutboxSource implements CosmicOutboxSource {
    private final DataSource dataSource;

    public JdbcCosmicOutboxSource(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public List<CosmicOutboxRecord> pending(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        String sql = "SELECT * FROM economy_transaction_outbox WHERE published_at IS NULL "
                + "ORDER BY created_at, outbox_id LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            List<CosmicOutboxRecord> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Number secondary = (Number) rows.getObject("secondary_character_id");
                    result.add(new CosmicOutboxRecord(UUID.fromString(rows.getString("outbox_id")),
                            rows.getString("idempotency_key"), rows.getString("operation_kind"),
                            rows.getInt("primary_character_id"),
                            secondary == null ? null : secondary.intValue(), rows.getString("summary"),
                            rows.getString("payload_json"),
                            rows.getTimestamp("created_at").toInstant()));
                }
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not poll Cosmic economy outbox", failure);
        }
    }

    @Override
    public void markPublished(UUID outboxId) {
        update(outboxId, "published_at = CURRENT_TIMESTAMP, delivery_attempts = delivery_attempts + 1, "
                + "last_delivery_error = NULL", null);
    }

    @Override
    public void markFailed(UUID outboxId, String error) {
        update(outboxId, "delivery_attempts = delivery_attempts + 1, last_delivery_error = ?",
                error == null ? "unknown delivery failure" : error);
    }

    private void update(UUID outboxId, String assignment, String error) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE economy_transaction_outbox SET " + assignment + " WHERE outbox_id = ?")) {
            int index = 1;
            if (error != null) statement.setString(index++, error.substring(0, Math.min(1024, error.length())));
            statement.setString(index, outboxId.toString());
            if (statement.executeUpdate() != 1) throw new SQLException("outbox row is missing");
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not update Cosmic economy outbox", failure);
        }
    }
}
