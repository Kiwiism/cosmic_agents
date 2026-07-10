/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    Copyleft (L) 2016 - 2019 RonanLana (HeavenMS)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client.processor.npc;

import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ItemFactory;
import client.inventory.ModifyInventory;
import client.inventory.manipulator.InventoryManipulator;
import net.server.Server;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.maps.HiredMerchant;
import service.NoteService;
import tools.DatabaseConnection;
import tools.PacketCreator;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import static java.util.concurrent.TimeUnit.DAYS;

/**
 * @author RonanLana - synchronization of Fredrick modules and operation results
 */
public class FredrickProcessor {
    private static final Logger log = LoggerFactory.getLogger(FredrickProcessor.class);
    public static final String LOAD_ERROR_MESSAGE =
            "Fredrick could not load your stored items. Nothing was changed; please try again shortly.";
    private static final int[] dailyReminders = new int[]{2, 5, 10, 15, 30, 60, 90, Integer.MAX_VALUE};

    private final NoteService noteService;

    public FredrickProcessor(NoteService noteService) {
        this.noteService = noteService;
    }

    public static void logLoadFailure(Character chr, String operation, SQLException failure) {
        log.error("Failed to {} Fredrick storage for chr {} (id {}, account {})",
                operation, chr.getName(), chr.getId(), chr.getAccountID(), failure);
    }

    public static void notifyLoadFailure(Character chr, String operation, SQLException failure) {
        logLoadFailure(chr, operation, failure);
        chr.dropMessage(1, LOAD_ERROR_MESSAGE);
    }

    private static byte canRetrieveFromFredrick(Character chr, List<Pair<Item, InventoryType>> items,
                                                 int merchantMeso) {
        if (!Inventory.checkSpotsAndOwnership(chr, items)) {
            List<Integer> itemids = new LinkedList<>();
            for (Pair<Item, InventoryType> it : items) {
                itemids.add(it.getLeft().getItemId());
            }

            if (chr.canHoldUniques(itemids)) {
                return 0x22;
            } else {
                return 0x20;
            }
        }

        int netMeso = merchantMeso;
        if (netMeso > 0) {
            if (!chr.canHoldMeso(netMeso)) {
                return 0x1F;
            }
        } else {
            if (chr.getMeso() < -1 * netMeso) {
                return 0x21;
            }
        }

        return 0x0;
    }

    public static int timestampElapsedDays(Timestamp then, long timeNow) {
        return (int) ((timeNow - then.getTime()) / DAYS.toMillis(1));
    }

    private static String fredrickReminderMessage(int daynotes) {
        String msg;

        if (daynotes < 4) {
            msg = "Hi customer! I am Fredrick, the Union Chief of the Hired Merchant Union. A reminder that " + dailyReminders[daynotes] + " days have passed since you used our service. Please reclaim your stored goods at FM Entrance.";
        } else {
            msg = "Hi customer! I am Fredrick, the Union Chief of the Hired Merchant Union. " + dailyReminders[daynotes] + " days have passed since you used our service. Consider claiming back the items before we move them away for refund.";
        }

        return msg;
    }

    public static void removeFredrickLog(int cid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            removeFredrickLog(con, cid);
        } catch (SQLException sqle) {
            monitoring.RuntimeFailureLogger.log(sqle);
        }
    }

    public static void removeFredrickLog(Connection con, int cid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM `fredstorage` WHERE `cid` = ?")) {
            ps.setInt(1, cid);
            ps.executeUpdate();
        }
    }

    public static void insertFredrickLog(int cid) {
        try (Connection con = DatabaseConnection.getConnection()) {
            insertFredrickLog(con, cid);
        } catch (SQLException sqle) {
            monitoring.RuntimeFailureLogger.log(sqle);
        }
    }

    public static void insertFredrickLog(Connection con, int cid) throws SQLException {
        removeFredrickLog(con, cid);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO `fredstorage` (`cid`, `daynotes`, `timestamp`) VALUES (?, 0, ?)")) {
            ps.setInt(1, cid);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    private static void removeFredrickReminders(List<Pair<Integer, Integer>> expiredCids) {
        List<String> expiredCnames = new LinkedList<>();
        for (Pair<Integer, Integer> id : expiredCids) {
            String name = Character.getNameById(id.getLeft());
            if (name != null) {
                expiredCnames.add(name);
            }
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM `notes` WHERE `from` LIKE ? AND `to` LIKE ?")) {
            ps.setString(1, "FREDRICK");

            for (String cname : expiredCnames) {
                ps.setString(2, cname);
                ps.executeBatch();
            }
        } catch (SQLException e) {
            monitoring.RuntimeFailureLogger.log(e);
        }
    }

    public void runFredrickSchedule() {
        try (Connection con = DatabaseConnection.getConnection()) {
            List<Pair<Integer, Integer>> expiredCids = new LinkedList<>();
            List<Pair<Pair<Integer, String>, Integer>> notifCids = new LinkedList<>();
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM fredstorage f LEFT JOIN (SELECT id, name, world, lastLogoutTime FROM characters) AS c ON c.id = f.cid");
                 ResultSet rs = ps.executeQuery()) {
                long curTime = System.currentTimeMillis();

                while (rs.next()) {
                    int cid = rs.getInt("cid");
                    int world = rs.getInt("world");
                    Timestamp ts = rs.getTimestamp("timestamp");
                    int daynotes = Math.min(dailyReminders.length - 1, rs.getInt("daynotes"));

                    int elapsedDays = timestampElapsedDays(ts, curTime);
                    if (elapsedDays > 100) {
                        expiredCids.add(new Pair<>(cid, world));
                    } else {
                        int notifDay = dailyReminders[daynotes];

                        if (elapsedDays >= notifDay) {
                            do {
                                daynotes++;
                                notifDay = dailyReminders[daynotes];
                            } while (elapsedDays >= notifDay);

                            Timestamp logoutTs = rs.getTimestamp("lastLogoutTime");
                            int inactivityDays = timestampElapsedDays(logoutTs, curTime);

                            if (inactivityDays < 7 || daynotes >= dailyReminders.length - 1) {  // don't spam inactive players
                                String name = rs.getString("name");
                                notifCids.add(new Pair<>(new Pair<>(cid, name), daynotes));
                            }
                        }
                    }
                }

            }

            if (!expiredCids.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM `inventoryitems` WHERE `type` = ? AND `characterid` = ?")) {
                    ps.setInt(1, ItemFactory.MERCHANT.getValue());

                    for (Pair<Integer, Integer> cid : expiredCids) {
                        ps.setInt(2, cid.getLeft());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                try (PreparedStatement ps = con.prepareStatement("UPDATE `characters` SET `MerchantMesos` = 0 WHERE `id` = ?")) {
                    for (Pair<Integer, Integer> cid : expiredCids) {
                        ps.setInt(1, cid.getLeft());
                        ps.addBatch();

                        World wserv = Server.getInstance().getWorld(cid.getRight());
                        if (wserv != null) {
                            Character chr = wserv.getPlayerStorage().getCharacterById(cid.getLeft());
                            if (chr != null) {
                                chr.setMerchantMeso(0);
                            }
                        }
                    }

                    ps.executeBatch();
                }

                removeFredrickReminders(expiredCids);

                try (PreparedStatement ps = con.prepareStatement("DELETE FROM `fredstorage` WHERE `cid` = ?")) {
                    for (Pair<Integer, Integer> cid : expiredCids) {
                        ps.setInt(1, cid.getLeft());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }
            }

            if (!notifCids.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement("UPDATE `fredstorage` SET `daynotes` = ? WHERE `cid` = ?")) {
                    for (Pair<Pair<Integer, String>, Integer> cid : notifCids) {
                        ps.setInt(1, cid.getRight());
                        ps.setInt(2, cid.getLeft().getLeft());
                        ps.addBatch();

                        String msg = fredrickReminderMessage(cid.getRight() - 1);
                        noteService.sendNormal(msg, "FREDRICK", cid.getLeft().getRight());
                    }

                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            monitoring.RuntimeFailureLogger.log(e);
        }
    }

    private static List<Pair<Item, InventoryType>> snapshotInventory(Character chr) {
        List<Pair<Item, InventoryType>> snapshot = new ArrayList<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD) {
                continue;
            }
            for (Item item : chr.getInventory(type).list()) {
                snapshot.add(new Pair<>(item.copy(), type));
            }
        }
        return snapshot;
    }

    private static void restoreInventory(Character chr, List<Pair<Item, InventoryType>> snapshot) {
        List<ModifyInventory> updates = new ArrayList<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD) {
                continue;
            }
            Inventory inventory = chr.getInventory(type);
            for (Item item : inventory.list()) {
                updates.add(new ModifyInventory(3, item));
                inventory.removeSlot(item.getPosition());
            }
        }
        for (Pair<Item, InventoryType> pair : snapshot) {
            Item item = pair.getLeft().copy();
            chr.getInventory(pair.getRight()).addItemFromDB(item);
            updates.add(new ModifyInventory(0, item));
        }
        for (int from = 0; from < updates.size(); from += 200) {
            int to = Math.min(from + 200, updates.size());
            chr.sendPacket(PacketCreator.modifyInventory(true, updates.subList(from, to)));
        }
    }

    static int netMerchantMeso(int merchantMeso, Timestamp storedAt, long now) {
        int elapsedDays = storedAt == null ? 0 : timestampElapsedDays(storedAt, now);
        elapsedDays = Math.clamp(elapsedDays, 0, 100);
        return (int) (((long) merchantMeso * (100 - elapsedDays)) / 100);
    }

    private static MerchantBalance loadMerchantBalanceForUpdate(Connection con, int cid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT c.MerchantMesos, f.timestamp FROM characters c " +
                        "LEFT JOIN fredstorage f ON f.cid = c.id WHERE c.id = ? FOR UPDATE")) {
            ps.setInt(1, cid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Character no longer exists: " + cid);
                }
                int storedMeso = rs.getInt("MerchantMesos");
                return new MerchantBalance(storedMeso,
                        netMerchantMeso(storedMeso, rs.getTimestamp("timestamp"), System.currentTimeMillis()));
            }
        }
    }

    private record MerchantBalance(int storedMeso, int netMeso) {
    }

    static void persistRetrieval(Connection con, Character chr,
                                 List<Pair<Item, InventoryType>> inventorySnapshot,
                                 int expectedMerchantMeso, int settledMeso) throws SQLException {
        ItemFactory.INVENTORY.saveItems(inventorySnapshot, chr.getId(), con);

        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM inventorymerchant WHERE characterid = ?")) {
            ps.setInt(1, chr.getId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE inventoryitems, inventoryequipment FROM inventoryitems " +
                        "LEFT JOIN inventoryequipment USING(inventoryitemid) " +
                        "WHERE type = ? AND characterid = ?")) {
            ps.setInt(1, ItemFactory.MERCHANT.getValue());
            ps.setInt(2, chr.getId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET meso = ?, MerchantMesos = 0 WHERE id = ? AND MerchantMesos = ?")) {
            ps.setInt(1, settledMeso);
            ps.setInt(2, chr.getId());
            ps.setInt(3, expectedMerchantMeso);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Merchant balance changed during Fredrick retrieval");
            }
        }
        removeFredrickLog(con, chr.getId());
    }

    public void fredrickRetrieveItems(Client c) {     // thanks Gustav for pointing out the dupe on Fredrick handling
        if (c.tryacquireClient()) {
            Character chr = c.getPlayer();
            try {
                synchronized (chr) {
                    List<Pair<Item, InventoryType>> originalInventory = null;
                    List<Pair<Item, InventoryType>> items;
                    int settledMeso;
                    try (Connection con = DatabaseConnection.getConnection()) {
                        con.setAutoCommit(false);
                        try {
                            MerchantBalance merchantBalance = loadMerchantBalanceForUpdate(con, chr.getId());
                            items = ItemFactory.MERCHANT.loadMerchantItemsForUpdate(chr.getId(), con);
                            byte response = canRetrieveFromFredrick(chr, items, merchantBalance.netMeso());
                            if (response != 0) {
                                con.rollback();
                                chr.sendPacket(PacketCreator.fredrickMessage(response));
                                return;
                            }

                            originalInventory = snapshotInventory(chr);
                            for (Pair<Item, InventoryType> it : items) {
                                if (!InventoryManipulator.addFromDrop(chr.getClient(), it.getLeft(), false)) {
                                    throw new SQLException("Inventory changed during Fredrick retrieval");
                                }
                            }

                            settledMeso = Math.addExact(chr.getMeso(), merchantBalance.netMeso());
                            persistRetrieval(con, chr, snapshotInventory(chr), merchantBalance.storedMeso(),
                                    settledMeso);
                            con.commit();
                        } catch (Exception failure) {
                            con.rollback();
                            if (originalInventory != null) {
                                restoreInventory(chr, originalInventory);
                            }
                            throw failure;
                        }
                    }

                    chr.applyCommittedMerchantRetrieval(settledMeso);
                    HiredMerchant merchant = chr.getHiredMerchant();
                    if (merchant != null) {
                        merchant.clearItems();
                    }
                    for (Pair<Item, InventoryType> it : items) {
                        Item item = it.getLeft();
                        String itemName = ItemInformationProvider.getInstance().getName(item.getItemId());
                        log.debug("Chr {} gained {}x {} ({})", chr.getName(), item.getQuantity(), itemName,
                                item.getItemId());
                    }
                    chr.sendPacket(PacketCreator.fredrickMessage((byte) 0x1E));
                }
            } catch (Exception ex) {
                SQLException sqlFailure = ex instanceof SQLException sql ? sql
                        : new SQLException("Fredrick retrieval failed", ex);
                notifyLoadFailure(chr, "retrieve", sqlFailure);
            } finally {
                c.releaseClient();
            }
        }
    }
}
