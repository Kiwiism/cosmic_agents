package server.maps;

import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Cosmic-side durable escrow; writes are enlisted in the economy settlement transaction. */
public final class PlayerShopEscrowStore {
    private PlayerShopEscrowStore() { }

    public static void persist(Connection connection, PlayerShopEscrowSnapshot snapshot) throws SQLException {
        if (snapshot.listings().isEmpty()) {
            delete(connection, snapshot.ownerCharacterId(), snapshot.escrowId());
            return;
        }
        String sql = "INSERT INTO economy_player_shop_escrow (owner_character_id, escrow_id, room_map_id, "
                + "spot_x, permit_item_id, description, listings_json) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE escrow_id = VALUES(escrow_id), room_map_id = VALUES(room_map_id), "
                + "spot_x = VALUES(spot_x), permit_item_id = VALUES(permit_item_id), "
                + "description = VALUES(description), listings_json = VALUES(listings_json), "
                + "updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, snapshot.ownerCharacterId());
            statement.setString(2, snapshot.escrowId());
            statement.setInt(3, snapshot.roomMapId());
            statement.setInt(4, snapshot.spotX());
            statement.setInt(5, snapshot.permitItemId());
            statement.setString(6, snapshot.description());
            statement.setString(7, snapshot.listingsJson());
            statement.executeUpdate();
        }
    }

    public static void delete(Connection connection, int ownerId, String escrowId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM economy_player_shop_escrow WHERE owner_character_id = ? AND escrow_id = ?")) {
            statement.setInt(1, ownerId);
            statement.setString(2, escrowId);
            statement.executeUpdate();
        }
    }

    public static Optional<PlayerShopEscrowSnapshot> load(int ownerId) {
        String sql = "SELECT * FROM economy_player_shop_escrow WHERE owner_character_id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ownerId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                List<PlayerShopEscrowSnapshot.Listing> listings =
                        PlayerShopEscrowSnapshot.decodeListings(row.getString("listings_json"));
                return Optional.of(new PlayerShopEscrowSnapshot(row.getString("escrow_id"), ownerId,
                        row.getInt("room_map_id"), row.getInt("spot_x"), row.getInt("permit_item_id"),
                        row.getString("description"), listings));
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not load player-shop escrow", failure);
        }
    }
}
