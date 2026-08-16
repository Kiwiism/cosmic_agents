package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.activity.ActivityCalibrationSample;
import server.agents.economy.activity.ActivityCalibrationSink;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

public final class JdbcActivityCalibrationStore implements ActivityCalibrationSink {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcActivityCalibrationStore(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public void append(ActivityCalibrationSample sample) {
        String sql = "INSERT INTO activity_calibration_sample (sample_id, agent_character_id, "
                + "agent_build, map_id, level, job_family, started_at, completed_at, kill_counts, "
                + "consumed_items, died) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(sample.sampleId()));
            statement.setInt(2, sample.agentCharacterId());
            statement.setString(3, sample.agentBuild());
            statement.setInt(4, sample.mapId());
            statement.setInt(5, sample.level());
            statement.setString(6, sample.jobFamily());
            statement.setTimestamp(7, Timestamp.from(sample.startedAt()));
            statement.setTimestamp(8, Timestamp.from(sample.completedAt()));
            statement.setString(9, JSON.writeValueAsString(sample.killCounts()));
            statement.setString(10, JSON.writeValueAsString(sample.consumedItems()));
            statement.setBoolean(11, sample.died());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not append activity calibration", failure);
        }
    }
}
