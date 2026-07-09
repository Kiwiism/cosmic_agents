package server.doubleagent;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.YamlConfig;
import constants.skills.Beginner;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;
import net.server.Server;
import net.server.world.World;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import tools.DatabaseConnection;
import tools.PacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DoubleAgentService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<Integer, Long> NEXT_ALLOWED_AT = new ConcurrentHashMap<>();

    private DoubleAgentService() {
    }

    public static boolean handleTagSkill(Character player, int skillId) {
        if (!YamlConfig.config.server.DOUBLE_AGENT_ENABLED || !isTagTrigger(skillId)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long nextAllowedAt = NEXT_ALLOWED_AT.getOrDefault(player.getId(), 0L);
        if (now < nextAllowedAt) {
            player.sendPacket(PacketCreator.enableActions());
            return true;
        }
        NEXT_ALLOWED_AT.put(player.getId(), now + Math.max(1000L, YamlConfig.config.server.DOUBLE_AGENT_COOLDOWN_MS));

        try {
            if (restoreActive(player, "DOUBLE_AGENT_BACK")) {
                player.message("Double Agent restored.");
                return true;
            }

            applyCounterpartOverlay(player);
            player.message("Double Agent active: " + player.getName() + " <-> " + counterpartName(player) + ".");
        } catch (Exception e) {
            player.message("Double Agent failed: " + e.getMessage());
            player.sendPacket(PacketCreator.enableActions());
        }
        return true;
    }

    public static void restoreActiveBeforeLogin(int characterId) {
        try {
            ActiveSession active = loadActiveSession(characterId);
            if (active == null) {
                return;
            }

            TagSnapshot base = fromJson(active.baseSnapshotJson());
            TagSnapshot overlay = fromJson(active.overlaySnapshotJson());
            try (Connection con = DatabaseConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    persistSnapshot(con, active.controlledCharacterId(), base);
                    persistSnapshot(con, active.counterpartCharacterId(), overlay);
                    markRestored(con, active.id(), "LOGIN_RECOVERY");
                    con.commit();
                } catch (SQLException | RuntimeException e) {
                    con.rollback();
                    markFailed(active.id(), e.getMessage());
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to restore active Double Agent session for character " + characterId, e);
        }
    }

    public static boolean restoreActive(Character player, String reason) {
        try {
            ActiveSession active = loadActiveSession(player.getId());
            if (active == null) {
                return false;
            }

            TagSnapshot base = fromJson(active.baseSnapshotJson());
            TagSnapshot overlay = fromJson(active.overlaySnapshotJson());
            try (Connection con = DatabaseConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    persistSnapshot(con, active.controlledCharacterId(), base);
                    persistSnapshot(con, active.counterpartCharacterId(), overlay);
                    markRestored(con, active.id(), reason);
                    con.commit();
                } catch (SQLException | RuntimeException e) {
                    con.rollback();
                    markFailed(active.id(), e.getMessage());
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            }
            applyLiveSnapshot(player, base);
            Character counterpart = activeCounterpartAgent(player, active.counterpartCharacterId());
            if (counterpart != null) {
                applyLiveSnapshot(counterpart, overlay);
                refreshAgentRuntimeSnapshot(player, counterpart);
            }
            return true;
        } catch (Exception e) {
            player.message("Double Agent restore failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean isOverlayActive(Character player) {
        try {
            return loadActiveSession(player.getId()) != null;
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean isTagTrigger(int skillId) {
        return skillId == Beginner.NIMBLE_FEET
                || skillId == Noblesse.NIMBLE_FEET
                || skillId == Legend.AGILE_BODY
                || skillId == Evan.NIMBLE_FEET;
    }

    private static void applyCounterpartOverlay(Character player) throws SQLException, JsonProcessingException {
        if (!isSafeToTag(player)) {
            throw new IllegalStateException("close trade/shop/storage/NPC/event state first");
        }

        Counterpart counterpart = findCounterpart(player);
        if (counterpart == null) {
            throw new IllegalStateException("missing same-account character " + counterpartName(player));
        }
        Character liveCounterpart = activeCounterpartAgent(player, counterpart.id());
        if (liveCounterpart == null && isOnline(counterpart.id())) {
            throw new IllegalStateException(counterpart.name() + " is online");
        }

        TagSnapshot base = captureLive(player);
        TagSnapshot overlay = liveCounterpart == null ? captureFromDb(counterpart.id()) : captureLive(liveCounterpart);
        String baseJson = toJson(base);
        String overlayJson = toJson(overlay);

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                persistSnapshot(con, player.getId(), overlay);
                persistSnapshot(con, counterpart.id(), base);
                insertActiveSession(con, player, counterpart.id(), baseJson, overlayJson);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }

        applyLiveSnapshot(player, overlay);
        if (liveCounterpart != null) {
            applyLiveSnapshot(liveCounterpart, base);
            refreshAgentRuntimeSnapshot(player, liveCounterpart);
        }
    }

    private static boolean isSafeToTag(Character player) {
        return player.getTrade() == null
                && player.getShop() == null
                && player.getMiniGame() == null
                && player.getHiredMerchant() == null
                && player.getEventInstance() == null
                && !player.getCashShop().isOpened()
                && player.getClient().getCM() == null
                && player.getClient().getQM() == null;
    }

    private static String counterpartName(Character player) {
        return player.getName() + "Agent";
    }

    private static Counterpart findCounterpart(Character player) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id, name FROM characters WHERE accountid = ? AND world = ? AND name = ?")) {
            ps.setInt(1, player.getAccountID());
            ps.setInt(2, player.getWorld());
            ps.setString(3, counterpartName(player));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Counterpart(rs.getInt("id"), rs.getString("name"));
            }
        }
    }

    private static boolean isOnline(int characterId) {
        for (World world : Server.getInstance().getWorlds()) {
            if (world != null && world.getPlayerStorage().getCharacterById(characterId) != null) {
                return true;
            }
        }
        return false;
    }

    private static Character activeCounterpartAgent(Character player, int counterpartId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), counterpartId);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null && agent.getAccountID() == player.getAccountID() ? agent : null;
    }

    private static void refreshAgentRuntimeSnapshot(Character player, Character agent) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), agent.getId());
        if (entry != null) {
            AgentMovementStateRuntime.refreshMovementProfile(entry, agent);
        }
    }

    private static TagSnapshot captureLive(Character player) {
        Map<Integer, SkillDto> skills = new HashMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : player.getSkills().entrySet()) {
            Character.SkillEntry skill = entry.getValue();
            skills.put(entry.getKey().getId(), new SkillDto(skill.skillevel, skill.masterlevel, skill.expiration));
        }

        Map<Integer, KeyDto> keymap = new HashMap<>();
        for (Map.Entry<Integer, KeyBinding> entry : player.getKeymap().entrySet()) {
            KeyBinding binding = entry.getValue();
            keymap.put(entry.getKey(), new KeyDto(binding.getType(), binding.getAction()));
        }

        List<EquipDto> equipped = new ArrayList<>();
        for (Item item : player.getInventory(InventoryType.EQUIPPED).list()) {
            equipped.add(EquipDto.from((Equip) item));
        }

        return new TagSnapshot(
                player.getJob().getId(),
                player.getLevel(),
                player.getGender(),
                player.getStr(),
                player.getDex(),
                player.getInt(),
                player.getLuk(),
                player.getHp(),
                player.getMp(),
                player.getMaxHp(),
                player.getMaxMp(),
                player.getRemainingAp(),
                player.getRemainingSps(),
                player.getSkinColor().getId(),
                player.getFace(),
                player.getHair(),
                skills,
                keymap,
                equipped);
    }

    private static TagSnapshot captureFromDb(int characterId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection()) {
            int job;
            int level;
            int gender;
            int str;
            int dex;
            int int_;
            int luk;
            int hp;
            int mp;
            int maxhp;
            int maxmp;
            int ap;
            int[] sp;
            int skin;
            int face;
            int hair;

            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("character not found: " + characterId);
                    }
                    job = rs.getInt("job");
                    level = rs.getInt("level");
                    gender = rs.getInt("gender");
                    str = rs.getInt("str");
                    dex = rs.getInt("dex");
                    int_ = rs.getInt("int");
                    luk = rs.getInt("luk");
                    hp = rs.getInt("hp");
                    mp = rs.getInt("mp");
                    maxhp = rs.getInt("maxhp");
                    maxmp = rs.getInt("maxmp");
                    ap = rs.getInt("ap");
                    sp = parseSp(rs.getString("sp"));
                    skin = rs.getInt("skincolor");
                    face = rs.getInt("face");
                    hair = rs.getInt("hair");
                }
            }

            return new TagSnapshot(job, level, gender, str, dex, int_, luk, hp, mp, maxhp, maxmp, ap, sp, skin, face, hair,
                    loadSkills(con, characterId),
                    loadKeymap(con, characterId),
                    loadEquipped(con, characterId));
        }
    }

    private static Map<Integer, SkillDto> loadSkills(Connection con, int characterId) throws SQLException {
        Map<Integer, SkillDto> skills = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT skillid, skilllevel, masterlevel, expiration FROM skills WHERE characterid = ?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    skills.put(rs.getInt("skillid"), new SkillDto(rs.getInt("skilllevel"), rs.getInt("masterlevel"), rs.getLong("expiration")));
                }
            }
        }
        return skills;
    }

    private static Map<Integer, KeyDto> loadKeymap(Connection con, int characterId) throws SQLException {
        Map<Integer, KeyDto> keymap = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT `key`, type, action FROM keymap WHERE characterid = ?")) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keymap.put(rs.getInt("key"), new KeyDto(rs.getInt("type"), rs.getInt("action")));
                }
            }
        }
        return keymap;
    }

    private static List<EquipDto> loadEquipped(Connection con, int characterId) throws SQLException {
        List<EquipDto> equipped = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("""
                SELECT * FROM inventoryitems
                LEFT JOIN inventoryequipment USING(inventoryitemid)
                WHERE type = 1 AND characterid = ? AND inventorytype = ?
                """)) {
            ps.setInt(1, characterId);
            ps.setInt(2, InventoryType.EQUIPPED.getType());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    equipped.add(EquipDto.from(rs));
                }
            }
        }
        return equipped;
    }

    private static void applyLiveSnapshot(Character player, TagSnapshot snapshot) {
        player.applyDoubleAgentStats(snapshot.job(), snapshot.level(), snapshot.gender(), snapshot.str(), snapshot.dex(), snapshot.int_(), snapshot.luk(),
                snapshot.hp(), snapshot.mp(), snapshot.maxhp(), snapshot.maxmp(), snapshot.ap(), snapshot.sp(),
                snapshot.skin(), snapshot.face(), snapshot.hair());
        player.replaceDoubleAgentEquipped(snapshot.toItems());

        Map<Integer, Character.SkillEntry> nextSkills = new HashMap<>();
        for (Map.Entry<Integer, SkillDto> entry : snapshot.skills().entrySet()) {
            SkillDto skill = entry.getValue();
            nextSkills.put(entry.getKey(), new Character.SkillEntry((byte) skill.level(), skill.masterLevel(), skill.expiration()));
        }
        player.replaceDoubleAgentSkills(nextSkills);

        player.getKeymap().clear();
        for (Map.Entry<Integer, KeyDto> entry : snapshot.keymap().entrySet()) {
            KeyDto key = entry.getValue();
            player.getKeymap().put(entry.getKey(), new KeyBinding(key.type(), key.action()));
        }
        player.sendPacket(PacketCreator.getKeymap(player.getKeymap()));
    }

    private static void persistSnapshot(Connection con, int characterId, TagSnapshot snapshot) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                UPDATE characters
                SET level = ?, gender = ?, str = ?, dex = ?, luk = ?, `int` = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?,
                    sp = ?, ap = ?, skincolor = ?, job = ?, hair = ?, face = ?
                WHERE id = ?
                """)) {
            ps.setInt(1, snapshot.level());
            ps.setInt(2, snapshot.gender());
            ps.setInt(3, snapshot.str());
            ps.setInt(4, snapshot.dex());
            ps.setInt(5, snapshot.luk());
            ps.setInt(6, snapshot.int_());
            ps.setInt(7, snapshot.hp());
            ps.setInt(8, snapshot.mp());
            ps.setInt(9, snapshot.maxhp());
            ps.setInt(10, snapshot.maxmp());
            ps.setString(11, encodeSp(snapshot.sp()));
            ps.setInt(12, snapshot.ap());
            ps.setInt(13, snapshot.skin());
            ps.setInt(14, snapshot.job());
            ps.setInt(15, snapshot.hair());
            ps.setInt(16, snapshot.face());
            ps.setInt(17, characterId);
            ps.executeUpdate();
        }

        replaceSkills(con, characterId, snapshot.skills());
        replaceKeymap(con, characterId, snapshot.keymap());
        replaceEquipped(con, characterId, snapshot.equipped());
    }

    private static void replaceSkills(Connection con, int characterId, Map<Integer, SkillDto> skills) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM skills WHERE characterid = ?")) {
            delete.setInt(1, characterId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = con.prepareStatement("INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)")) {
            for (Map.Entry<Integer, SkillDto> entry : skills.entrySet()) {
                SkillDto skill = entry.getValue();
                insert.setInt(1, characterId);
                insert.setInt(2, entry.getKey());
                insert.setInt(3, skill.level());
                insert.setInt(4, skill.masterLevel());
                insert.setLong(5, skill.expiration());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void replaceKeymap(Connection con, int characterId, Map<Integer, KeyDto> keymap) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM keymap WHERE characterid = ?")) {
            delete.setInt(1, characterId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = con.prepareStatement("INSERT INTO keymap (characterid, `key`, type, action) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<Integer, KeyDto> entry : keymap.entrySet()) {
                KeyDto key = entry.getValue();
                insert.setInt(1, characterId);
                insert.setInt(2, entry.getKey());
                insert.setInt(3, key.type());
                insert.setInt(4, key.action());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void replaceEquipped(Connection con, int characterId, List<EquipDto> equipped) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("""
                DELETE inventoryitems, inventoryequipment
                FROM inventoryitems LEFT JOIN inventoryequipment USING(inventoryitemid)
                WHERE inventoryitems.type = 1 AND inventoryitems.characterid = ? AND inventoryitems.inventorytype = ?
                """)) {
            delete.setInt(1, characterId);
            delete.setInt(2, InventoryType.EQUIPPED.getType());
            delete.executeUpdate();
        }

        try (PreparedStatement item = con.prepareStatement(
                "INSERT INTO inventoryitems VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement equip = con.prepareStatement(
                     "INSERT INTO inventoryequipment VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (EquipDto dto : equipped) {
                item.setInt(1, 1);
                item.setInt(2, characterId);
                item.setString(3, null);
                item.setInt(4, dto.itemId());
                item.setInt(5, InventoryType.EQUIPPED.getType());
                item.setInt(6, dto.position());
                item.setInt(7, 1);
                item.setString(8, dto.owner());
                item.setInt(9, dto.petId());
                item.setInt(10, dto.flag());
                item.setLong(11, dto.expiration());
                item.setString(12, dto.giftFrom());
                item.executeUpdate();

                try (ResultSet keys = item.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("missing generated inventory item id");
                    }
                    dto.bind(equip, keys.getInt(1));
                    equip.executeUpdate();
                }
            }
        }
    }

    private static ActiveSession loadActiveSession(int controlledCharacterId) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT id, controlled_characterid, counterpart_characterid, base_snapshot_json, overlay_snapshot_json
                     FROM character_tag_sessions
                     WHERE controlled_characterid = ? AND status = 'ACTIVE'
                     ORDER BY id DESC
                     LIMIT 1
                     """)) {
            ps.setInt(1, controlledCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ActiveSession(
                        rs.getInt("id"),
                        rs.getInt("controlled_characterid"),
                        rs.getInt("counterpart_characterid"),
                        rs.getString("base_snapshot_json"),
                        rs.getString("overlay_snapshot_json"));
            }
        }
    }

    private static void insertActiveSession(Connection con, Character player, int counterpartId, String baseJson, String overlayJson) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("""
                INSERT INTO character_tag_sessions
                (accountid, controlled_characterid, counterpart_characterid, status, base_snapshot_json, overlay_snapshot_json)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """)) {
            ps.setInt(1, player.getAccountID());
            ps.setInt(2, player.getId());
            ps.setInt(3, counterpartId);
            ps.setString(4, baseJson);
            ps.setString(5, overlayJson);
            ps.executeUpdate();
        }
    }

    private static void markRestored(Connection con, int sessionId, String reason) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE character_tag_sessions SET status = 'RESTORED', restored_at = CURRENT_TIMESTAMP, restore_reason = ? WHERE id = ?")) {
            ps.setString(1, reason);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        }
    }

    private static void markFailed(int sessionId, String reason) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE character_tag_sessions SET failure_reason = ? WHERE id = ?")) {
            ps.setString(1, reason);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private static String toJson(TagSnapshot snapshot) throws JsonProcessingException {
        return MAPPER.writeValueAsString(snapshot);
    }

    private static TagSnapshot fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, TagSnapshot.class);
    }

    private static int[] parseSp(String text) {
        String[] parts = text.split(",");
        int[] sp = new int[10];
        for (int i = 0; i < sp.length && i < parts.length; i++) {
            sp[i] = Integer.parseInt(parts[i]);
        }
        return sp;
    }

    private static String encodeSp(int[] sp) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sp.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(sp[i]);
        }
        return builder.toString();
    }

    private record Counterpart(int id, String name) {
    }

    private record ActiveSession(int id,
                                 int controlledCharacterId,
                                 int counterpartCharacterId,
                                 String baseSnapshotJson,
                                 String overlaySnapshotJson) {
    }

    private record TagSnapshot(int job,
                               int level,
                               int gender,
                               int str,
                               int dex,
                               int int_,
                               int luk,
                               int hp,
                               int mp,
                               int maxhp,
                               int maxmp,
                               int ap,
                               int[] sp,
                               int skin,
                               int face,
                               int hair,
                               Map<Integer, SkillDto> skills,
                               Map<Integer, KeyDto> keymap,
                               List<EquipDto> equipped) {
        private List<Item> toItems() {
            List<Item> items = new ArrayList<>();
            for (EquipDto equip : equipped) {
                items.add(equip.toEquip());
            }
            return items;
        }
    }

    private record SkillDto(int level, int masterLevel, long expiration) {
    }

    private record KeyDto(int type, int action) {
    }

    private record EquipDto(int itemId,
                            short position,
                            String owner,
                            int petId,
                            short flag,
                            long expiration,
                            String giftFrom,
                            byte upgradeSlots,
                            byte level,
                            short str,
                            short dex,
                            short int_,
                            short luk,
                            short hp,
                            short mp,
                            short watk,
                            short matk,
                            short wdef,
                            short mdef,
                            short acc,
                            short avoid,
                            short hands,
                            short speed,
                            short jump,
                            short vicious,
                            byte itemLevel,
                            int itemExp,
                            int ringId) {
        static EquipDto from(Equip equip) {
            return new EquipDto(equip.getItemId(), equip.getPosition(), equip.getOwner(), equip.getPetId(), equip.getFlag(),
                    equip.getExpiration(), equip.getGiftFrom(), equip.getUpgradeSlots(), equip.getLevel(), equip.getStr(),
                    equip.getDex(), equip.getInt(), equip.getLuk(), equip.getHp(), equip.getMp(), equip.getWatk(),
                    equip.getMatk(), equip.getWdef(), equip.getMdef(), equip.getAcc(), equip.getAvoid(), equip.getHands(),
                    equip.getSpeed(), equip.getJump(), equip.getVicious(), equip.getItemLevel(), equip.getItemExp(), equip.getRingId());
        }

        static EquipDto from(ResultSet rs) throws SQLException {
            return new EquipDto(rs.getInt("itemid"), (short) rs.getInt("position"), rs.getString("owner"), rs.getInt("petid"),
                    (short) rs.getInt("flag"), rs.getLong("expiration"), rs.getString("giftFrom"), (byte) rs.getInt("upgradeslots"),
                    rs.getByte("level"), (short) rs.getInt("str"), (short) rs.getInt("dex"), (short) rs.getInt("int"),
                    (short) rs.getInt("luk"), (short) rs.getInt("hp"), (short) rs.getInt("mp"), (short) rs.getInt("watk"),
                    (short) rs.getInt("matk"), (short) rs.getInt("wdef"), (short) rs.getInt("mdef"), (short) rs.getInt("acc"),
                    (short) rs.getInt("avoid"), (short) rs.getInt("hands"), (short) rs.getInt("speed"), (short) rs.getInt("jump"),
                    (short) rs.getInt("vicious"), rs.getByte("itemlevel"), rs.getInt("itemexp"), rs.getInt("ringid"));
        }

        Equip toEquip() {
            Equip equip = new Equip(itemId, position, upgradeSlots);
            equip.setOwner(owner == null ? "" : owner);
            equip.setFlag(flag);
            equip.setExpiration(expiration);
            equip.setGiftFrom(giftFrom == null ? "" : giftFrom);
            equip.setLevel(level);
            equip.setStr(str);
            equip.setDex(dex);
            equip.setInt(int_);
            equip.setLuk(luk);
            equip.setHp(hp);
            equip.setMp(mp);
            equip.setWatk(watk);
            equip.setMatk(matk);
            equip.setWdef(wdef);
            equip.setMdef(mdef);
            equip.setAcc(acc);
            equip.setAvoid(avoid);
            equip.setHands(hands);
            equip.setSpeed(speed);
            equip.setJump(jump);
            equip.setVicious(vicious);
            equip.setItemLevel(itemLevel);
            equip.setItemExp(itemExp);
            equip.setRingId(ringId);
            return equip;
        }

        void bind(PreparedStatement ps, int inventoryItemId) throws SQLException {
            ps.setInt(1, inventoryItemId);
            ps.setInt(2, upgradeSlots);
            ps.setInt(3, level);
            ps.setInt(4, str);
            ps.setInt(5, dex);
            ps.setInt(6, int_);
            ps.setInt(7, luk);
            ps.setInt(8, hp);
            ps.setInt(9, mp);
            ps.setInt(10, watk);
            ps.setInt(11, matk);
            ps.setInt(12, wdef);
            ps.setInt(13, mdef);
            ps.setInt(14, acc);
            ps.setInt(15, avoid);
            ps.setInt(16, hands);
            ps.setInt(17, speed);
            ps.setInt(18, jump);
            ps.setInt(19, 0);
            ps.setInt(20, vicious);
            ps.setInt(21, itemLevel);
            ps.setInt(22, itemExp);
            ps.setInt(23, ringId);
        }
    }
}
