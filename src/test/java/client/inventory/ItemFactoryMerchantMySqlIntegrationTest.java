package client.inventory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.DatabaseConnection;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ItemFactoryMerchantMySqlIntegrationTest {
    private static int characterId;

    @BeforeAll
    static void connect() {
        assumeTrue(Boolean.getBoolean("cosmic.test.mysql"),
                "Enable explicitly with -Dcosmic.test.mysql=true");
        assertTrue(DatabaseConnection.initializeConnectionPool());
        characterId = 1_900_000_000 + ThreadLocalRandom.current().nextInt(10_000_000);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (characterId == 0) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteFixture(con);
        }
    }

    @Test
    void loadsBundleQuantityInsideCallerTransaction() throws Exception {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int inventoryItemId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO inventoryitems " +
                                "(type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom) " +
                                "VALUES (?, ?, NULL, 2000000, ?, 1, 2, '', -1, 0, -1, '')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, ItemFactory.MERCHANT.getValue());
                    ps.setInt(2, characterId);
                    ps.setInt(3, InventoryType.USE.getType());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        assertTrue(rs.next());
                        inventoryItemId = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO inventorymerchant (inventoryitemid, characterid, bundles) VALUES (?, ?, 3)")) {
                    ps.setInt(1, inventoryItemId);
                    ps.setInt(2, characterId);
                    ps.executeUpdate();
                }
                con.commit();

                con.setAutoCommit(false);
                List<Pair<Item, InventoryType>> loaded =
                        ItemFactory.MERCHANT.loadMerchantItemsForUpdate(characterId, con);

                assertEquals(1, loaded.size());
                assertEquals(InventoryType.USE, loaded.getFirst().getRight());
                assertEquals(6, loaded.getFirst().getLeft().getQuantity());
                con.rollback();
            } finally {
                con.setAutoCommit(true);
                deleteFixture(con);
            }
        }
    }

    @Test
    void rejectsQuantityThatCannotBeRepresentedByInventoryPackets() throws Exception {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int inventoryItemId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO inventoryitems " +
                                "(type, characterid, accountid, itemid, inventorytype, position, quantity, owner, petid, flag, expiration, giftFrom) " +
                                "VALUES (?, ?, NULL, 2000000, ?, 1, 2, '', -1, 0, -1, '')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, ItemFactory.MERCHANT.getValue());
                    ps.setInt(2, characterId);
                    ps.setInt(3, InventoryType.USE.getType());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        assertTrue(rs.next());
                        inventoryItemId = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO inventorymerchant (inventoryitemid, characterid, bundles) VALUES (?, ?, 20000)")) {
                    ps.setInt(1, inventoryItemId);
                    ps.setInt(2, characterId);
                    ps.executeUpdate();
                }
                con.commit();
                con.setAutoCommit(false);

                assertThrows(java.sql.SQLException.class,
                        () -> ItemFactory.MERCHANT.loadMerchantItemsForUpdate(characterId, con));
                con.rollback();
            } finally {
                con.setAutoCommit(true);
                deleteFixture(con);
            }
        }
    }

    private static void deleteFixture(Connection con) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM inventorymerchant WHERE characterid = ?")) {
            ps.setInt(1, characterId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE inventoryitems, inventoryequipment FROM inventoryitems " +
                        "LEFT JOIN inventoryequipment USING(inventoryitemid) " +
                        "WHERE inventoryitems.characterid = ? AND inventoryitems.type = ?")) {
            ps.setInt(1, characterId);
            ps.setInt(2, ItemFactory.MERCHANT.getValue());
            ps.executeUpdate();
        }
    }
}
