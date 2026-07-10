package client.processor.npc;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ItemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.DatabaseConnection;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FredrickRetrievalMySqlIntegrationTest {
    private static int accountId;
    private static int characterId;

    @BeforeAll
    static void connect() throws Exception {
        assumeTrue(Boolean.getBoolean("cosmic.test.mysql"),
                "Enable explicitly with -Dcosmic.test.mysql=true");
        assertTrue(DatabaseConnection.initializeConnectionPool());
        try (Connection con = DatabaseConnection.getConnection()) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO accounts (name, password) VALUES (?, '')", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "tx" + suffix);
                ps.executeUpdate();
                accountId = generatedId(ps);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO characters (accountid, name, meso, MerchantMesos) VALUES (?, ?, 100, 25)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, accountId);
                ps.setString(2, "tx" + suffix);
                ps.executeUpdate();
                characterId = generatedId(ps);
            }
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (characterId == 0) {
            return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteInventory(con);
            execute(con, "DELETE FROM fredstorage WHERE cid = ?", characterId);
            execute(con, "DELETE FROM characters WHERE id = ?", characterId);
            execute(con, "DELETE FROM accounts WHERE id = ?", accountId);
        }
    }

    @Test
    void commitsInventoryMerchantBalanceAndReminderTogether() throws Exception {
        resetFixture();
        Character character = character();
        Item delivered = new Item(2000000, (short) 1, (short) 6);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            FredrickProcessor.persistRetrieval(con, character,
                    List.of(new Pair<>(delivered, InventoryType.USE)), 25, 125);
            con.commit();
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            assertEquals(125, scalar(con, "SELECT meso FROM characters WHERE id = ?", characterId));
            assertEquals(0, scalar(con, "SELECT MerchantMesos FROM characters WHERE id = ?", characterId));
            assertEquals(1, scalar(con, "SELECT COUNT(*) FROM inventoryitems WHERE characterid = ? AND type = 1", characterId));
            assertEquals(0, scalar(con, "SELECT COUNT(*) FROM inventoryitems WHERE characterid = ? AND type = 6", characterId));
            assertEquals(0, scalar(con, "SELECT COUNT(*) FROM inventorymerchant WHERE characterid = ?", characterId));
            assertEquals(0, scalar(con, "SELECT COUNT(*) FROM fredstorage WHERE cid = ?", characterId));
        }
    }

    @Test
    void callerRollbackRestoresEveryFredrickRow() throws Exception {
        resetFixture();
        Character character = character();
        Item delivered = new Item(2000000, (short) 1, (short) 6);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            assertThrows(SQLException.class, () -> {
                FredrickProcessor.persistRetrieval(con, character,
                        List.of(new Pair<>(delivered, InventoryType.USE)), 25, 125);
                throw new SQLException("forced failure");
            });
            con.rollback();
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            assertEquals(100, scalar(con, "SELECT meso FROM characters WHERE id = ?", characterId));
            assertEquals(25, scalar(con, "SELECT MerchantMesos FROM characters WHERE id = ?", characterId));
            assertEquals(0, scalar(con, "SELECT COUNT(*) FROM inventoryitems WHERE characterid = ? AND type = 1", characterId));
            assertEquals(1, scalar(con, "SELECT COUNT(*) FROM inventoryitems WHERE characterid = ? AND type = 6", characterId));
            assertEquals(1, scalar(con, "SELECT COUNT(*) FROM inventorymerchant WHERE characterid = ?", characterId));
            assertEquals(1, scalar(con, "SELECT COUNT(*) FROM fredstorage WHERE cid = ?", characterId));
        }
    }

    private static Character character() {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(characterId);
        return character;
    }

    private static void resetFixture() throws Exception {
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteInventory(con);
            execute(con, "DELETE FROM fredstorage WHERE cid = ?", characterId);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE characters SET meso = 100, MerchantMesos = 25 WHERE id = ?")) {
                ps.setInt(1, characterId);
                ps.executeUpdate();
            }

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
                inventoryItemId = generatedId(ps);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO inventorymerchant (inventoryitemid, characterid, bundles) VALUES (?, ?, 3)")) {
                ps.setInt(1, inventoryItemId);
                ps.setInt(2, characterId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO fredstorage (cid, daynotes, timestamp) VALUES (?, 0, CURRENT_TIMESTAMP)")) {
                ps.setInt(1, characterId);
                ps.executeUpdate();
            }
        }
    }

    private static void deleteInventory(Connection con) throws Exception {
        execute(con, "DELETE FROM inventorymerchant WHERE characterid = ?", characterId);
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE inventoryitems, inventoryequipment FROM inventoryitems " +
                        "LEFT JOIN inventoryequipment USING(inventoryitemid) WHERE inventoryitems.characterid = ?")) {
            ps.setInt(1, characterId);
            ps.executeUpdate();
        }
    }

    private static int generatedId(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static int scalar(Connection con, String sql, int id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private static void execute(Connection con, String sql, int id) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
