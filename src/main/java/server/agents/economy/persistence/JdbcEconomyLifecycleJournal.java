package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.activity.FarmSessionOutcome;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.scenario.EconomyRunCoordinator;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Permanent lifecycle evidence used by recovery and future dashboard projections. */
public final class JdbcEconomyLifecycleJournal implements EconomyLifecycleJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomyLifecycleJournal(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void admitted(UUID runId, EconomyAgentProfile profile, Instant logicalAt) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement arrival = connection.prepareStatement(
                    "INSERT INTO population_arrival (run_id, agent_id, logical_at, arrival_kind) "
                            + "VALUES (?, ?, ?, 'SCHEDULED') ON CONFLICT DO NOTHING");
                 PreparedStatement agent = connection.prepareStatement(
                         "INSERT INTO agent_economic_profile VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)) "
                                 + "ON CONFLICT (run_id, agent_id) DO NOTHING")) {
                arrival.setObject(1, runId); arrival.setString(2, profile.agentId());
                arrival.setTimestamp(3, Timestamp.from(logicalAt)); arrival.executeUpdate();
                agent.setObject(1, runId); agent.setString(2, profile.agentId()); agent.setString(3, profile.jobFamily());
                agent.setDouble(4, profile.dailyActivityFraction()); agent.setDouble(5, profile.riskTolerance());
                agent.setDouble(6, profile.liquidityPreference()); agent.setDouble(7, profile.upgradeAggressiveness());
                agent.setDouble(8, profile.shoppingPatience()); agent.setDouble(9, profile.stallWillingness());
                agent.setInt(10, profile.priceMemoryHours()); agent.setDouble(11, profile.negotiationAggressiveness());
                agent.setDouble(12, profile.chairInterest()); agent.setString(13, json(profile)); agent.executeUpdate();
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not journal population admission", failure);
        }
    }

    @Override
    public void activityStarted(UUID runId, FarmSessionPlan plan) {
        String sql = "INSERT INTO activity_session (run_id, activity_id, agent_id, calibration_id, map_id, "
                + "started_at, due_at, status, explicit_work) VALUES (?, ?, ?, ?, ?, ?, ?, 'STARTED', "
                + "CAST(? AS jsonb)) ON CONFLICT (run_id, activity_id) DO NOTHING";
        update(sql, statement -> {
            statement.setObject(1, runId); statement.setString(2, plan.sessionId());
            statement.setString(3, plan.agentId()); statement.setString(4, plan.calibrationId());
            statement.setInt(5, plan.mapId()); statement.setTimestamp(6, Timestamp.from(plan.startedAt()));
            statement.setTimestamp(7, Timestamp.from(plan.startedAt().plus(plan.duration())));
            statement.setString(8, json(planEvidence(plan)));
        }, "Could not journal activity start");
    }

    @Override
    public void activityCompleted(UUID runId, FarmSessionOutcome outcome) {
        String sql = "UPDATE activity_session SET completed_at = ?, status = 'COMPLETED', outcome = "
                + "CAST(? AS jsonb) WHERE run_id = ? AND activity_id = ? AND status = 'STARTED'";
        update(sql, statement -> {
            statement.setTimestamp(1, Timestamp.from(outcome.completedAt()));
            statement.setString(2, json(outcomeEvidence(outcome)));
            statement.setObject(3, runId); statement.setString(4, outcome.sessionId());
        }, "Could not journal activity completion");
    }

    @Override
    public void stateChanged(UUID runId, String agentId, EconomyRunCoordinator.Status state,
                             String activityId, Instant logicalAt) {
        String sql = "INSERT INTO agent_lifecycle_state VALUES (?, ?, ?, ?, ?) ON CONFLICT (run_id, agent_id) "
                + "DO UPDATE SET state = EXCLUDED.state, logical_at = EXCLUDED.logical_at, "
                + "activity_id = EXCLUDED.activity_id";
        update(sql, statement -> {
            statement.setObject(1, runId); statement.setString(2, agentId); statement.setString(3, state.name());
            statement.setTimestamp(4, Timestamp.from(logicalAt)); statement.setString(5, activityId);
        }, "Could not journal lifecycle state");
    }

    @Override
    public void presence(UUID runId, String agentId, server.agents.economy.scenario.EconomyWorldPort.Presence presence,
                         String reason, Instant logicalAt) {
        String raw = runId + ":" + agentId + ':' + logicalAt + ':' + reason + ':'
                + presence.mapId() + ':' + presence.x() + ':' + presence.y() + ':' + presence.visible();
        UUID id = UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sql = "INSERT INTO agent_presence_event (presence_id, run_id, agent_id, logical_at, map_id, "
                + "x, y, visible, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id); statement.setObject(2, runId); statement.setString(3, agentId);
            statement.setTimestamp(4, Timestamp.from(logicalAt)); statement.setInt(5, presence.mapId());
            statement.setInt(6, presence.x()); statement.setInt(7, presence.y());
            statement.setBoolean(8, presence.visible()); statement.setString(9, reason);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not journal agent presence", failure);
        }
    }

    private void update(String sql, Binder binder, String message) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) throw new SQLException("expected exactly one affected row");
        } catch (SQLException failure) {
            throw new EconomyPersistenceException(message, failure);
        }
    }

    private static Map<String, Object> planEvidence(FarmSessionPlan plan) {
        return Map.of("duration", plan.duration().toString(), "dropRateMultiplier", plan.dropRateMultiplier(),
                "deathProbabilityPerHour", plan.deathProbabilityPerHour(),
                "respawnDowntime", plan.respawnDowntime().toString(),
                "monsters", plan.monsters(), "activeQuestIds", plan.activeQuestIds(),
                "consumedItems", plan.consumedItems());
    }

    private static Map<String, Object> outcomeEvidence(FarmSessionOutcome outcome) {
        return Map.of("calibrationId", outcome.calibrationId(), "experience", outcome.experience(),
                "mesos", outcome.mesos(), "itemDrops", outcome.itemDrops(),
                "consumedItems", outcome.consumedItems(), "killCounts", outcome.killCounts(),
                "death", outcome.death());
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new EconomyPersistenceException("Could not encode evidence", failure); }
    }

    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
}
