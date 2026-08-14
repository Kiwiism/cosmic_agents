package server.agents.economy.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;

public final class JdbcPostgresOutboxSink implements CosmicOutboxSink {
    private final DataSource dataSource;

    public JdbcPostgresOutboxSink(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public void accept(CosmicOutboxRecord record) {
        String sql = "INSERT INTO cosmic_outbox_receipt (outbox_id, idempotency_key, operation_kind, "
                + "primary_character_id, secondary_character_id, summary, cosmic_created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (outbox_id) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, record.outboxId());
            statement.setString(2, record.idempotencyKey());
            statement.setString(3, record.operationKind());
            statement.setInt(4, record.primaryCharacterId());
            if (record.secondaryCharacterId() == null) statement.setNull(5, java.sql.Types.INTEGER);
            else statement.setInt(5, record.secondaryCharacterId());
            statement.setString(6, record.summary());
            statement.setTimestamp(7, Timestamp.from(record.createdAt()));
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not receive Cosmic economy outbox row", failure);
        }
    }
}
