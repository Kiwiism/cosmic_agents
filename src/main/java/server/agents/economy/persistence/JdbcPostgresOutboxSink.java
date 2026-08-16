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
                + "primary_character_id, secondary_character_id, summary, payload, run_id, logical_at, "
                + "decision_id, activity_id, config_revision, catalog_revision, reason_code, "
                + "primary_is_agent, secondary_is_agent, cosmic_created_at) VALUES (?, ?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (outbox_id) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, record.outboxId());
            statement.setString(2, record.idempotencyKey());
            statement.setString(3, record.operationKind());
            statement.setInt(4, record.primaryCharacterId());
            if (record.secondaryCharacterId() == null) statement.setNull(5, java.sql.Types.INTEGER);
            else statement.setInt(5, record.secondaryCharacterId());
            statement.setString(6, record.summary());
            statement.setString(7, record.payloadJson());
            statement.setObject(8, record.runId());
            statement.setTimestamp(9, record.logicalAt() == null ? null : Timestamp.from(record.logicalAt()));
            statement.setString(10, record.decisionId()); statement.setString(11, record.activityId());
            statement.setString(12, record.configRevision()); statement.setString(13, record.catalogRevision());
            statement.setString(14, record.reasonCode()); statement.setBoolean(15, record.primaryIsAgent());
            statement.setBoolean(16, record.secondaryIsAgent());
            statement.setTimestamp(17, Timestamp.from(record.createdAt()));
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not receive Cosmic economy outbox row", failure);
        }
    }
}
