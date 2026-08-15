package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.market.StallOffer;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JdbcStallOfferStore implements StallOfferStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcStallOfferStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public List<StallOffer> pendingForSeller(UUID runId, String sellerAgentId, Instant asOf, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("offer query limit must be positive");
        String sql = "SELECT offer_id,buyer_id,seller_id,stall_id,listing_id,room_map_id,item_id,"
                + "item_fingerprint,item_attributes,quantity,ask_mesos,offered_mesos,public_text,"
                + "created_at,expires_at,status FROM stall_offer WHERE run_id=? AND seller_id=? "
                + "AND status='PENDING' AND created_at<=? ORDER BY created_at,offer_id LIMIT ?";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, sellerAgentId);
            statement.setTimestamp(3, Timestamp.from(asOf)); statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                var offers = new java.util.ArrayList<StallOffer>();
                while (rows.next()) offers.add(read(runId, rows));
                return List.copyOf(offers);
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not read pending stall offers", failure);
        }
    }

    @Override
    public void create(StallOffer offer) {
        String sql = "INSERT INTO stall_offer (offer_id,run_id,buyer_id,seller_id,stall_id,listing_id,"
                + "room_map_id,item_id,item_fingerprint,item_attributes,quantity,ask_mesos,offered_mesos,"
                + "public_text,created_at,expires_at,status) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),"
                + "?,?,?,?,?,?,?) ON CONFLICT (offer_id) DO NOTHING";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, offer.offerId()); statement.setObject(2, offer.runId());
            statement.setString(3, offer.buyerAgentId()); statement.setString(4, offer.sellerAgentId());
            statement.setString(5, offer.stallId()); statement.setString(6, offer.listingId());
            statement.setInt(7, offer.roomMapId()); statement.setInt(8, offer.itemId());
            statement.setString(9, offer.itemFingerprint());
            statement.setString(10, JSON.writeValueAsString(offer.itemAttributes()));
            statement.setInt(11, offer.quantity()); statement.setLong(12, offer.askMesos());
            statement.setLong(13, offer.offeredMesos()); statement.setString(14, offer.publicText());
            statement.setTimestamp(15, Timestamp.from(offer.createdAt()));
            statement.setTimestamp(16, Timestamp.from(offer.expiresAt()));
            statement.setString(17, offer.status().name());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not create stall offer", failure);
        }
    }

    @Override
    public void resolve(UUID offerId, StallOffer.Status status, String response,
                        Instant respondedAt, String settlementTransactionId) {
        if (status == StallOffer.Status.PENDING)
            throw new IllegalArgumentException("resolved stall offer cannot remain pending");
        String sql = "UPDATE stall_offer SET status=?,response_text=?,responded_at=?,"
                + "settlement_transaction_id=? WHERE offer_id=? "
                + "AND status IN ('PENDING','ACCEPTED_AWAITING_SETTLEMENT')";
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name()); statement.setString(2, response);
            statement.setTimestamp(3, Timestamp.from(respondedAt));
            statement.setString(4, settlementTransactionId); statement.setObject(5, offerId);
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not resolve stall offer", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static StallOffer read(UUID runId, ResultSet rows)
            throws SQLException, JsonProcessingException {
        Map<String, Object> attributes = JSON.readValue(rows.getString("item_attributes"), Map.class);
        return new StallOffer(rows.getObject("offer_id", UUID.class), runId,
                rows.getString("buyer_id"), rows.getString("seller_id"), rows.getString("stall_id"),
                rows.getString("listing_id"), rows.getInt("room_map_id"), rows.getInt("item_id"),
                rows.getString("item_fingerprint"), attributes, rows.getInt("quantity"),
                rows.getLong("ask_mesos"), rows.getLong("offered_mesos"), rows.getString("public_text"),
                rows.getTimestamp("created_at").toInstant(), rows.getTimestamp("expires_at").toInstant(),
                StallOffer.Status.valueOf(rows.getString("status")));
    }
}
