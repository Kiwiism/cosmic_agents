package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.ownership.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable, append-oriented audit for inventory ownership decisions and mutation authority. */
public final class JdbcEconomyOwnershipJournal implements EconomyOwnershipJournal {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomyOwnershipJournal(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void appendReview(InventoryReview review) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                revokePrevious(connection, review);
                insertReview(connection, review);
                for (InventoryDispositionDecision decision : review.decisions())
                    insertDecision(connection, review.reviewId(), decision);
                for (InventoryReview.AssetReservation reservation : review.reservations())
                    insertReservation(connection, review, reservation);
                for (InventoryReview.ActionAuthorization authorization : review.authorizations())
                    insertAuthorization(connection, review, authorization);
                connection.commit();
            } catch (SQLException | JsonProcessingException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not append inventory ownership review", failure);
        }
    }

    @Override
    public void markAuthorizationConsumed(UUID authorizationId, Instant at) {
        String sql = "UPDATE economic_action_authorization SET status='CONSUMED', consumed_at=? "
                + "WHERE authorization_id=? AND status='ACTIVE'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(at)); statement.setObject(2, authorizationId);
            if (statement.executeUpdate() != 1)
                throw new EconomyPersistenceException("Economic authorization is not active", null);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not consume economic authorization", failure);
        }
    }

    @Override
    public void appendGuardEvent(UUID runId, String agentId, int characterId, Instant at,
                                 String action, InventoryItemRef item, int quantity,
                                 boolean allowed, String reason, UUID authorizationId) {
        String sql = "INSERT INTO economic_action_guard_event (guard_event_id, run_id, agent_id, "
                + "character_id, logical_time, action, inventory_type, inventory_slot, item_id, "
                + "item_fingerprint, quantity, allowed, reason, authorization_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, runId);
            statement.setString(3, agentId); statement.setInt(4, characterId);
            statement.setTimestamp(5, Timestamp.from(at)); statement.setString(6, action);
            statement.setString(7, item.inventoryType()); statement.setShort(8, item.slot());
            statement.setInt(9, item.itemId()); statement.setString(10, item.fingerprint());
            statement.setInt(11, quantity); statement.setBoolean(12, allowed);
            statement.setString(13, reason); statement.setObject(14, authorizationId);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not append economic guard event", failure);
        }
    }

    private static void revokePrevious(Connection connection, InventoryReview review) throws SQLException {
        for (String table : new String[]{"economic_asset_reservation", "economic_action_authorization"}) {
            String sql = "UPDATE " + table + " SET status='REVOKED' WHERE run_id=? AND agent_id=? AND status='ACTIVE'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, review.runId()); statement.setString(2, review.agentId());
                statement.executeUpdate();
            }
        }
    }

    private static void insertReview(Connection connection, InventoryReview review)
            throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO inventory_review (review_id, run_id, agent_id, character_id, "
                + "logical_time, purpose, inventory_revision, snapshot) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, review.reviewId()); statement.setObject(2, review.runId());
            statement.setString(3, review.agentId()); statement.setInt(4, review.snapshot().characterId());
            statement.setTimestamp(5, Timestamp.from(review.logicalTime()));
            statement.setString(6, review.purpose().name()); statement.setString(7, review.snapshot().revision());
            statement.setString(8, JSON.writeValueAsString(review.snapshot().items())); statement.executeUpdate();
        }
    }

    private static void insertDecision(Connection connection, UUID reviewId,
                                       InventoryDispositionDecision decision) throws SQLException {
        String sql = "INSERT INTO item_disposition_decision (decision_id, review_id, inventory_type, "
                + "inventory_slot, item_id, item_fingerprint, quantity, disposition, reason, legacy_action, "
                + "shadow_action, shadow_disagreement) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, reviewId);
            bindItem(statement, 3, decision.item()); statement.setInt(7, decision.quantity());
            statement.setString(8, decision.disposition().name()); statement.setString(9, decision.reason());
            statement.setString(10, decision.legacyAction()); statement.setString(11, decision.shadowAction());
            statement.setBoolean(12, decision.shadowDisagreement()); statement.executeUpdate();
        }
    }

    private static void insertReservation(Connection connection, InventoryReview review,
                                          InventoryReview.AssetReservation reservation) throws SQLException {
        String sql = "INSERT INTO economic_asset_reservation (reservation_id, review_id, run_id, agent_id, "
                + "inventory_type, inventory_slot, item_id, item_fingerprint, quantity, action, venue, status, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, reservation.reservationId()); statement.setObject(2, review.reviewId());
            statement.setObject(3, review.runId()); statement.setString(4, review.agentId());
            bindItem(statement, 5, reservation.item()); statement.setInt(9, reservation.quantity());
            statement.setString(10, reservation.action()); statement.setString(11, reservation.venue());
            statement.setTimestamp(12, Timestamp.from(review.logicalTime())); statement.executeUpdate();
        }
    }

    private static void insertAuthorization(Connection connection, InventoryReview review,
                                            InventoryReview.ActionAuthorization authorization) throws SQLException {
        String sql = "INSERT INTO economic_action_authorization (authorization_id, review_id, run_id, agent_id, "
                + "inventory_type, inventory_slot, item_id, item_fingerprint, quantity, action, venue, "
                + "inventory_revision, status, issued_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, authorization.authorizationId()); statement.setObject(2, review.reviewId());
            statement.setObject(3, review.runId()); statement.setString(4, review.agentId());
            bindItem(statement, 5, authorization.item()); statement.setInt(9, authorization.quantity());
            statement.setString(10, authorization.action()); statement.setString(11, authorization.venue());
            statement.setString(12, authorization.inventoryRevision());
            statement.setTimestamp(13, Timestamp.from(review.logicalTime()));
            statement.setTimestamp(14, Timestamp.from(authorization.expiresAt())); statement.executeUpdate();
        }
    }

    private static void bindItem(PreparedStatement statement, int start, InventoryItemRef item)
            throws SQLException {
        statement.setString(start, item.inventoryType()); statement.setShort(start + 1, item.slot());
        statement.setInt(start + 2, item.itemId()); statement.setString(start + 3, item.fingerprint());
    }
}
