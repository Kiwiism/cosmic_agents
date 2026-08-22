package server.agents.integration.cosmic;

import client.Job;
import client.creator.CharacterFactoryRecipe;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ItemFactory;
import client.inventory.Pet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.YamlConfig;
import constants.game.GameConstants;
import constants.id.MapId;
import server.ItemInformationProvider;
import server.agents.administration.AgentCleanSlatePreview;
import server.agents.administration.AgentCleanSlateResetPort;
import server.agents.administration.AgentCleanSlateTarget;
import server.agents.integration.AgentIdentityStatus;
import tools.DatabaseConnection;
import tools.Pair;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic Cosmic persistence boundary for the offline Agent clean-slate workflow. */
public enum CosmicAgentCleanSlateResetPort implements AgentCleanSlateResetPort {
    INSTANCE;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int STARTER_TOP_ITEM_ID = 1_040_002;
    private static final int STARTER_BOTTOM_ITEM_ID = 1_060_002;
    private static final int STARTER_SHOES_ITEM_ID = 1_072_001;
    private static final int STARTER_WEAPON_ITEM_ID = 1_302_000;

    @Override
    public AgentCleanSlateTarget inspect(int characterId) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return inspect(connection, characterId, false);
        }
    }

    @Override
    public void recordPreview(AgentCleanSlatePreview preview,
                              String requestedBy,
                              String reason,
                              String confirmationHash,
                              long previewedAtMs) throws Exception {
        String status = preview.eligible() ? "PREVIEWED" : "PREVIEW_BLOCKED";
        String previewJson = previewJson(preview);
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO agent_reset_audit "
                             + "(reset_id, character_id, character_name, requested_by, reason, status, "
                             + "confirmation_hash, preview_json, previewed_at, expires_at) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, preview.resetId());
            statement.setInt(2, preview.target().characterId());
            statement.setString(3, preview.target().name());
            statement.setString(4, bounded(requestedBy, 64));
            statement.setString(5, bounded(reason, 512));
            statement.setString(6, status);
            statement.setString(7, confirmationHash);
            statement.setString(8, previewJson);
            statement.setTimestamp(9, new Timestamp(previewedAtMs));
            statement.setTimestamp(10, new Timestamp(preview.expiresAtMs()));
            statement.executeUpdate();
        }
    }

    @Override
    public AgentCleanSlateTarget resetGameplay(String resetId,
                                               int characterId,
                                               String expectedFingerprint,
                                               long executedAtMs) throws Exception {
        List<Pair<Item, InventoryType>> currentItems = ItemFactory.INVENTORY.loadItems(characterId, false);
        List<Pair<Item, InventoryType>> retainedItems = currentItems.stream()
                .filter(CosmicAgentCleanSlateResetPort::retainAcrossReset)
                .toList();
        List<Integer> discardedPetIds = currentItems.stream()
                .filter(pair -> !retainAcrossReset(pair))
                .map(pair -> pair.getLeft().getPetId())
                .filter(petId -> petId > 0)
                .distinct()
                .toList();
        List<Pair<Item, InventoryType>> cleanInventory = new ArrayList<>(retainedItems);
        cleanInventory.addAll(starterEquipment());

        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                AgentCleanSlateTarget locked = inspect(connection, characterId, true);
                if (!locked.fingerprint().equals(expectedFingerprint)) {
                    throw new IllegalStateException("character state changed after the reset preview");
                }
                requireResetEligible(locked);
                resetCharacterRow(connection, characterId, executedAtMs);
                clearProgressionTables(connection, characterId);
                resetKeymap(connection, characterId);
                for (Integer petId : discardedPetIds) {
                    Pet.deleteFromDb(connection, petId);
                }
                ItemFactory.INVENTORY.saveItems(cleanInventory, characterId, connection);
                markSucceeded(connection, resetId, executedAtMs, retainedItems.size());
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
        return inspect(characterId);
    }

    @Override
    public void markRejected(String resetId, String reason, long executedAtMs) throws Exception {
        updateAudit(resetId, "REJECTED", reason, executedAtMs);
    }

    @Override
    public void markCleanupWarning(String resetId, String warning) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_reset_audit SET status = 'SUCCEEDED_WITH_WARNINGS', "
                             + "failure_reason = ? WHERE reset_id = ? AND status = 'SUCCEEDED'")) {
            statement.setString(1, bounded(warning, 1024));
            statement.setString(2, resetId);
            statement.executeUpdate();
        }
    }

    private AgentCleanSlateTarget inspect(
            Connection connection, int characterId, boolean lockRow) throws Exception {
        String sql = "SELECT c.name, c.accountid, c.world, c.level, c.job, c.map, c.exp, c.meso, "
                + "c.HasMerchant, c.MerchantMesos, ai.status, ai.interactive_allowed, "
                + "a.banned, a.banreason, "
                + "(SELECT COUNT(*) FROM inventoryitems ii WHERE ii.characterid = c.id AND ii.type = 1) item_count, "
                + "(SELECT COALESCE(MAX(ii.inventoryitemid), 0) FROM inventoryitems ii "
                + " WHERE ii.characterid = c.id AND ii.type = 1) item_high_water, "
                + "(SELECT COUNT(*) FROM queststatus qs WHERE qs.characterid = c.id) quest_count, "
                + "(SELECT COUNT(*) FROM skills s WHERE s.characterid = c.id) skill_count, "
                + "(SELECT COUNT(*) FROM inventorymerchant im WHERE im.characterid = c.id) merchant_items, "
                + "(SELECT COUNT(*) FROM economy_player_shop_escrow es "
                + " WHERE es.owner_character_id = c.id) escrow_count "
                + "FROM characters c JOIN accounts a ON a.id = c.accountid "
                + "LEFT JOIN agent_characters ai ON ai.character_id = c.id WHERE c.id = ?"
                + (lockRow ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, characterId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("unknown character " + characterId);
                }
                List<Pair<Item, InventoryType>> items = ItemFactory.INVENTORY.loadItems(characterId, false);
                int preserved = (int) items.stream().filter(
                        CosmicAgentCleanSlateResetPort::retainAcrossReset).count();
                int itemCount = result.getInt("item_count");
                boolean active = AgentIdentityStatus.ACTIVE.name().equals(result.getString("status"));
                boolean dedicated = result.getBoolean("banned")
                        && CosmicAgentBackingAccountSecurity.AGENT_ONLY_BAN_REASON.equals(
                        result.getString("banreason"));
                boolean merchantClear = !result.getBoolean("HasMerchant")
                        && result.getInt("MerchantMesos") == 0
                        && result.getInt("merchant_items") == 0
                        && result.getInt("escrow_count") == 0;
                String fingerprintSource = characterId + "|" + result.getString("name") + "|"
                        + result.getInt("accountid") + "|" + result.getInt("world") + "|"
                        + result.getInt("level") + "|" + result.getInt("job") + "|"
                        + result.getInt("map") + "|" + result.getInt("exp") + "|"
                        + result.getInt("meso") + "|" + itemCount + "|"
                        + result.getLong("item_high_water") + "|" + result.getInt("quest_count") + "|"
                        + result.getInt("skill_count") + "|" + result.getString("status") + "|"
                        + result.getBoolean("interactive_allowed") + "|" + merchantClear;
                return new AgentCleanSlateTarget(
                        characterId, result.getString("name"), result.getInt("accountid"),
                        result.getInt("world"), result.getInt("level"), result.getInt("job"),
                        result.getInt("map"), result.getInt("exp"), result.getInt("meso"),
                        Math.max(0, itemCount - preserved), preserved,
                        result.getInt("quest_count"), result.getInt("skill_count"),
                        active, result.getBoolean("interactive_allowed"), dedicated, merchantClear,
                        digest(fingerprintSource));
            }
        }
    }

    private static void requireResetEligible(AgentCleanSlateTarget target) {
        if (!target.activeAgent() || target.interactiveAllowed()
                || !target.dedicatedAccount() || !target.merchantStateClear()) {
            throw new IllegalStateException("Agent no longer satisfies clean-slate safety requirements");
        }
    }

    private static void resetCharacterRow(
            Connection connection, int characterId, long executedAtMs) throws SQLException {
        CharacterFactoryRecipe baseline = new CharacterFactoryRecipe(
                Job.BEGINNER, 1, MapId.HENESYS,
                STARTER_TOP_ITEM_ID, STARTER_BOTTOM_ITEM_ID,
                STARTER_SHOES_ITEM_ID, STARTER_WEAPON_ITEM_ID);
        String sql = "UPDATE characters SET level = 1, exp = 0, gachaexp = 0, "
                + "str = ?, dex = ?, luk = ?, `int` = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, "
                + "hpMpUsed = 0, job = 0, fame = 0, fquest = 0, ap = ?, "
                + "sp = '0,0,0,0,0,0,0,0,0,0', map = ?, spawnpoint = 0, meso = 0, "
                + "MerchantMesos = 0, HasMerchant = 0, mountlevel = 1, mountexp = 0, "
                + "mounttiredness = 0, monsterbookcover = 0, vanquisherStage = 0, "
                + "ariantPoints = 0, dojoPoints = 0, lastDojoStage = 0, "
                + "finishedDojoTutorial = 0, vanquisherKills = 0, summonValue = 0, "
                + "reborns = 0, PQPoints = 0, "
                + "dataString = '', jailexpire = 0, lastExpGainTime = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, baseline.getStr());
            statement.setInt(2, baseline.getDex());
            statement.setInt(3, baseline.getLuk());
            statement.setInt(4, baseline.getInt());
            statement.setInt(5, baseline.getMaxHp());
            statement.setInt(6, baseline.getMaxMp());
            statement.setInt(7, baseline.getMaxHp());
            statement.setInt(8, baseline.getMaxMp());
            statement.setInt(9, baseline.getRemainingAp());
            statement.setInt(10, MapId.HENESYS);
            statement.setTimestamp(11, new Timestamp(executedAtMs));
            statement.setInt(12, characterId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Agent character row changed during reset");
            }
        }
    }

    private static void clearProgressionTables(Connection connection, int characterId)
            throws SQLException {
        String[] characterIdTables = {
                "medalmaps", "questprogress", "queststatus", "skills", "skillmacros",
                "eventstats", "savedlocations", "trocklocations"
        };
        for (String table : characterIdTables) {
            delete(connection, "DELETE FROM `" + table + "` WHERE characterid = ?", characterId);
        }
        String[] charIdTables = {"cooldowns", "playerdiseases", "area_info", "monsterbook"};
        for (String table : charIdTables) {
            delete(connection, "DELETE FROM `" + table + "` WHERE charid = ?", characterId);
        }
    }

    private static void resetKeymap(Connection connection, int characterId) throws SQLException {
        delete(connection, "DELETE FROM keymap WHERE characterid = ?", characterId);
        int[] keys = GameConstants.getCustomKey(YamlConfig.config.server.USE_CUSTOM_KEYSET);
        int[] types = GameConstants.getCustomType(YamlConfig.config.server.USE_CUSTOM_KEYSET);
        int[] actions = GameConstants.getCustomAction(YamlConfig.config.server.USE_CUSTOM_KEYSET);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)")) {
            for (int index = 0; index < keys.length; index++) {
                statement.setInt(1, characterId);
                statement.setInt(2, keys[index]);
                statement.setInt(3, types[index]);
                statement.setInt(4, actions[index]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static List<Pair<Item, InventoryType>> starterEquipment() {
        ItemInformationProvider items = ItemInformationProvider.getInstance();
        return List.of(
                equipped(items, STARTER_TOP_ITEM_ID, (byte) -5),
                equipped(items, STARTER_BOTTOM_ITEM_ID, (byte) -6),
                equipped(items, STARTER_SHOES_ITEM_ID, (byte) -7),
                equipped(items, STARTER_WEAPON_ITEM_ID, (byte) -11));
    }

    private static Pair<Item, InventoryType> equipped(
            ItemInformationProvider items, int itemId, byte position) {
        Item item = items.getEquipById(itemId);
        if (item == null) throw new IllegalStateException("missing starter equipment " + itemId);
        item.setPosition(position);
        return new Pair<>(item, InventoryType.EQUIPPED);
    }

    private static boolean retainAcrossReset(Pair<Item, InventoryType> pair) {
        Item item = pair.getLeft();
        return pair.getRight() == InventoryType.CASH
                || ItemInformationProvider.getInstance().isCash(item.getItemId())
                || item instanceof Equip equip && equip.getRingId() > -1;
    }

    private static void markSucceeded(
            Connection connection, String resetId, long executedAtMs, int retainedItems)
            throws SQLException, JsonProcessingException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE agent_reset_audit SET status = 'SUCCEEDED', result_json = ?, "
                        + "executed_at = ? WHERE reset_id = ? AND status = 'PREVIEWED'")) {
            statement.setString(1, JSON.writeValueAsString(Map.of(
                    "baseline", "LEVEL_1_BEGINNER",
                    "mapId", MapId.HENESYS,
                    "retainedItems", retainedItems)));
            statement.setTimestamp(2, new Timestamp(executedAtMs));
            statement.setString(3, resetId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("reset audit is missing or no longer pending");
            }
        }
    }

    private static void delete(Connection connection, String sql, int characterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, characterId);
            statement.executeUpdate();
        }
    }

    private static String previewJson(AgentCleanSlatePreview preview)
            throws JsonProcessingException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("target", preview.target());
        value.put("eligible", preview.eligible());
        value.put("blockers", preview.blockers());
        value.put("resetScope", preview.resetScope());
        value.put("retainedScope", preview.retainedScope());
        value.put("confirmationPhrase", preview.confirmationPhrase());
        return JSON.writeValueAsString(value);
    }

    private static void updateAudit(
            String resetId, String status, String reason, long executedAtMs) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE agent_reset_audit SET status = ?, failure_reason = ?, executed_at = ? "
                             + "WHERE reset_id = ? AND status = 'PREVIEWED'")) {
            statement.setString(1, status);
            statement.setString(2, bounded(reason, 1024));
            statement.setTimestamp(3, new Timestamp(executedAtMs));
            statement.setString(4, resetId);
            statement.executeUpdate();
        }
    }

    private static String bounded(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
