package server.agents.economy.persistence;

import server.agents.economy.catalog.CatalogBundleDescriptor;
import server.agents.economy.scenario.LoadedEconomyConfig;
import server.agents.economy.scenario.SimulationRunEngine;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSimulationRunRepository implements SimulationRunRepository {
    private final DataSource dataSource;
    private final RunCheckpointCodec checkpoints = new RunCheckpointCodec();

    public JdbcSimulationRunRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public void create(UUID runId, LoadedEconomyConfig loaded, CatalogBundleDescriptor catalog) {
        Instant start = Instant.parse(loaded.config().clock.logicalStart);
        Instant target = start.plus(Duration.ofDays(loaded.config().scenario.targetLogicalDays));
        String sql = "INSERT INTO simulation_run (run_id, scenario_id, status, logical_started_at, "
                + "logical_current_at, target_logical_at, seed, config_hash, config_yaml, catalog_version) "
                + "VALUES (?, ?, 'CREATED', ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setObject(1, runId);
                    statement.setString(2, loaded.config().scenario.id);
                    statement.setTimestamp(3, Timestamp.from(start));
                    statement.setTimestamp(4, Timestamp.from(start));
                    statement.setTimestamp(5, Timestamp.from(target));
                    statement.setLong(6, loaded.config().scenario.seed);
                    statement.setString(7, loaded.sha256());
                    statement.setString(8, loaded.rawYaml());
                    statement.setString(9, catalog.version());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO economy_config_revision (run_id, revision, effective_logical_at, "
                                + "config_hash, config_yaml, normalized_config, config_schema_version, "
                                + "validation_result, reason) VALUES (?, 0, ?, ?, ?, CAST(? AS jsonb), ?, "
                                + "CAST(? AS jsonb), 'INITIAL_VALIDATED_CONFIGURATION')")) {
                    statement.setObject(1, runId);
                    statement.setTimestamp(2, Timestamp.from(start));
                    statement.setString(3, loaded.sha256());
                    statement.setString(4, loaded.rawYaml());
                    statement.setString(5, loaded.normalizedJson());
                    statement.setInt(6, loaded.config().schemaVersion);
                    statement.setString(7, "{\"valid\":true,\"validator\":\"EconomyConfigValidator\"}");
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not create simulation run", failure);
        }
    }

    @Override
    public void updateLogicalTime(UUID runId, Instant logicalTime, String status) {
        String sql = "UPDATE simulation_run SET logical_current_at = ?, status = ?, "
                + "completed_at = CASE WHEN ? = 'COMPLETED' THEN now() ELSE completed_at END WHERE run_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(logicalTime));
            statement.setString(2, status);
            statement.setString(3, status);
            statement.setObject(4, runId);
            if (statement.executeUpdate() != 1) throw new SQLException("simulation run is missing");
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not update simulation run", failure);
        }
    }

    @Override
    public void saveCheckpoint(SimulationRunEngine.RunCheckpoint checkpoint) {
        RunCheckpointCodec.Encoded encoded = checkpoints.encode(checkpoint);
        String sql = "INSERT INTO simulation_checkpoint (checkpoint_id, run_id, logical_time, sequence, "
                + "config_hash, catalog_version, state, state_hash) VALUES (?, ?, ?, "
                + "COALESCE((SELECT MAX(sequence) + 1 FROM simulation_checkpoint WHERE run_id = ?), 0), "
                + "?, ?, CAST(? AS jsonb), ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, checkpoint.runId());
            statement.setTimestamp(3, Timestamp.from(checkpoint.logicalTime()));
            statement.setObject(4, checkpoint.runId());
            statement.setString(5, checkpoint.configHash());
            statement.setString(6, checkpoint.catalogVersion());
            statement.setString(7, encoded.json());
            statement.setString(8, encoded.sha256());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not save simulation checkpoint", failure);
        }
    }

    @Override
    public Optional<SimulationRunEngine.RunCheckpoint> latestCheckpoint(UUID runId) {
        String sql = "SELECT state::text, state_hash FROM simulation_checkpoint WHERE run_id = ? "
                + "ORDER BY sequence DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(checkpoints.decode(row.getString(1), row.getString(2)))
                        : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not load simulation checkpoint", failure);
        }
    }
}
