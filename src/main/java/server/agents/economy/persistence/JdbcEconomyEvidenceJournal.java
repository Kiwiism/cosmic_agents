package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.market.MarketObservation;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;

public final class JdbcEconomyEvidenceJournal implements EconomyEvidenceJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomyEvidenceJournal(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void appendDecision(DecisionEvidence evidence) {
        String sql = "INSERT INTO decision_journal (decision_id, run_id, agent_id, logical_time, "
                + "decision_kind, chosen_action, alternatives, beliefs_used, needs_used, utility_breakdown, "
                + "random_stream, random_draw, config_hash, catalog_version) VALUES (?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), CAST(? AS jsonb), "
                + "CAST(? AS jsonb), ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, evidence.decisionId());
            statement.setObject(2, evidence.runId());
            statement.setString(3, evidence.agentId());
            statement.setTimestamp(4, Timestamp.from(evidence.logicalTime()));
            statement.setString(5, evidence.decisionKind());
            statement.setString(6, JSON.writeValueAsString(evidence.chosenAction()));
            statement.setString(7, JSON.writeValueAsString(evidence.alternatives()));
            statement.setString(8, JSON.writeValueAsString(evidence.beliefsUsed()));
            statement.setString(9, JSON.writeValueAsString(evidence.needsUsed()));
            statement.setString(10, JSON.writeValueAsString(evidence.utilityBreakdown()));
            statement.setString(11, evidence.randomStream());
            if (evidence.randomDraw() == null) statement.setNull(12, Types.DOUBLE);
            else statement.setDouble(12, evidence.randomDraw());
            statement.setString(13, evidence.configHash());
            statement.setString(14, evidence.catalogVersion());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not append decision evidence", failure);
        }
    }

    @Override
    public void appendObservation(UUID runId, MarketObservation observation) {
        String sql = "INSERT INTO market_observation (observation_id, run_id, agent_id, logical_time, "
                + "room_map_id, stall_owner_id, item_id, quantity, unit_price, listing_id, observed_state, "
                + "quantity_per_bundle, bundles, bundle_price) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.fromString(observation.observationId()));
            statement.setObject(2, runId);
            statement.setString(3, observation.observerAgentId());
            statement.setTimestamp(4, Timestamp.from(observation.observedAt()));
            statement.setInt(5, observation.roomMapId());
            statement.setString(6, observation.stallOwnerAgentId());
            statement.setInt(7, observation.itemId());
            statement.setInt(8, observation.quantity());
            statement.setLong(9, observation.unitPrice());
            statement.setString(10, observation.listingId());
            statement.setString(11, observation.state().name());
            statement.setInt(12, observation.quantityPerBundle());
            statement.setInt(13, observation.bundles());
            statement.setLong(14, observation.bundlePrice());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not append market observation", failure);
        }
    }

    @Override
    public void appendSocial(SocialEvidence evidence) {
        String sql = "INSERT INTO social_event (social_event_id, run_id, logical_time, room_map_id, "
                + "speaker_agent_id, target_agent_id, event_kind, public_text, structured_intent, "
                + "related_item_id, related_event_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, evidence.socialEventId());
            statement.setObject(2, evidence.runId());
            statement.setTimestamp(3, Timestamp.from(evidence.logicalTime()));
            statement.setInt(4, evidence.roomMapId());
            statement.setString(5, evidence.speakerAgentId());
            statement.setString(6, evidence.targetAgentId());
            statement.setString(7, evidence.eventKind());
            statement.setString(8, evidence.publicText());
            statement.setString(9, JSON.writeValueAsString(evidence.structuredIntent()));
            if (evidence.relatedItemId() == null) statement.setNull(10, Types.INTEGER);
            else statement.setInt(10, evidence.relatedItemId());
            statement.setObject(11, evidence.relatedEconomicEventId());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not append social evidence", failure);
        }
    }
}
