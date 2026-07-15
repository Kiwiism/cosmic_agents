/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
package server;

import client.Client;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.trade.AgentOwnerItemNotificationService;
import tools.DatabaseConnection;
import tools.PacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Matze
 */
public class Shop {
    private static final Logger log = LoggerFactory.getLogger(Shop.class);
    private static final Set<Integer> rechargeableItems = new LinkedHashSet<>();

    private final int id;
    private final int npcId;
    private final List<ShopItem> items;

    static {
        for (int throwingStarId : ItemId.allThrowingStarIds()) {
            rechargeableItems.add(throwingStarId);
        }
        rechargeableItems.add(ItemId.BLAZE_CAPSULE);
        rechargeableItems.add(ItemId.GLAZE_CAPSULE);
        rechargeableItems.add(ItemId.BALANCED_FURY);
        rechargeableItems.remove(ItemId.DEVIL_RAIN_THROWING_STAR); // doesn't exist
        for (int bulletId : ItemId.allBulletIds()) {
            rechargeableItems.add(bulletId);
        }
    }

    Shop(int id, int npcId) {
        this.id = id;
        this.npcId = npcId;
        items = new ArrayList<>();
    }

    void addItem(ShopItem item) {
        items.add(item);
    }

    public List<ShopItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void sendShop(Client c) {
        c.getPlayer().setShop(this);
        c.sendPacket(PacketCreator.getNPCShop(c, getNpcId(), items));
    }

    public enum TransactionResult { SUCCESS, NOT_ENOUGH_MESO, NO_SPACE, INVALID }

    /** Core buy logic without response packets. Usable by bots.
     *  For rechargeable items, {@code quantity} is ignored — one slotMax stack is purchased. */
    public TransactionResult buyDirect(client.Character player, short slot, int itemId, short quantity) {
        ShopItem item = findBySlot(slot);
        if (item == null || item.getItemId() != itemId || quantity < 1) {
            return TransactionResult.INVALID;
        }
        if (item.getPrice() <= 0) {
            return TransactionResult.INVALID; // bots only use meso purchases
        }
        if (ItemConstants.isMedal(itemId)
                && (quantity != 1 || player.haveItemWithId(itemId, true))) {
            return TransactionResult.INVALID;
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Client c = player.getClient();
        if (!ItemConstants.isRechargeable(itemId)) {
            Integer amount = checkedCost(item.getPrice(), quantity);
            if (amount == null) {
                return TransactionResult.INVALID;
            }
            if (player.getMeso() < amount) {
                return TransactionResult.NOT_ENOUGH_MESO;
            }
            if (!InventoryManipulator.checkSpace(c, itemId, quantity, "")) {
                return TransactionResult.NO_SPACE;
            }
            InventoryManipulator.addById(c, itemId, quantity, "", -1);
            player.gainMeso(-amount, false);
        } else {
            short slotMax = ii.getSlotMax(c, item.getItemId());
            if (player.getMeso() < item.getPrice()) {
                return TransactionResult.NOT_ENOUGH_MESO;
            }
            if (!InventoryManipulator.checkSpace(c, itemId, slotMax, "")) {
                return TransactionResult.NO_SPACE;
            }
            InventoryManipulator.addById(c, itemId, slotMax, "", -1);
            player.gainMeso(-item.getPrice(), false);
        }
        return TransactionResult.SUCCESS;
    }

    /** Core recharge logic without response packets. Usable by bots. */
    public TransactionResult rechargeDirect(client.Character player, short slot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Item item = player.getInventory(InventoryType.USE).getItem(slot);
        if (item == null || !ItemConstants.isRechargeable(item.getItemId())) {
            return TransactionResult.INVALID;
        }
        short slotMax = ii.getSlotMax(player.getClient(), item.getItemId());
        if (item.getQuantity() < 0) {
            return TransactionResult.INVALID;
        }
        if (item.getQuantity() >= slotMax) {
            return TransactionResult.SUCCESS; // already full
        }
        int price = (int) Math.ceil(ii.getUnitPrice(item.getItemId()) * (slotMax - item.getQuantity()));
        if (player.getMeso() < price) {
            return TransactionResult.NOT_ENOUGH_MESO;
        }
        item.setQuantity(slotMax);
        player.forceUpdateItem(item);
        player.gainMeso(-price, false, true, false);
        return TransactionResult.SUCCESS;
    }

    public void buy(Client c, short slot, int itemId, short quantity) {
        ShopItem item = findBySlot(slot);
        if (item == null || quantity < 1) {
            c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
            return;
        }
        if (item.getItemId() != itemId) {
            log.warn("Wrong item {} for slot {} in shop {}", itemId, slot, id);
            c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
            return;
        }
        if (item.getPrice() <= 0 && item.getPitch() <= 0) {
            // Zero-price rechargeable rows are included only so the client can recharge owned stacks.
            c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
            return;
        }
        if (ItemConstants.isMedal(itemId)) {
            if (quantity != 1) {
                c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
                return;
            }
            if (c.getPlayer().haveItemWithId(itemId, true)) {
                // Acknowledge the shop action without the client's generic error dialog;
                // the notice below is the only duplicate-medal popup the player needs.
                c.sendPacket(PacketCreator.shopTransaction((byte) 0));
                c.sendPacket(PacketCreator.serverNotice(1, "You already have that medal."));
                return;
            }
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (item.getPrice() > 0) {
            Integer amount = checkedCost(item.getPrice(), quantity);
            if (amount == null) {
                c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
                return;
            }
            if (c.getPlayer().getMeso() >= amount) {
                if (InventoryManipulator.checkSpace(c, itemId, quantity, "")) {
                    if (!ItemConstants.isRechargeable(itemId)) { //Pets can't be bought from shops
                        InventoryManipulator.addById(c, itemId, quantity, "", -1);
                        c.getPlayer().gainMeso(-amount, false);
                    } else {
                        short slotMax = ii.getSlotMax(c, item.getItemId());
                        quantity = slotMax;
                        InventoryManipulator.addById(c, itemId, quantity, "", -1);
                        c.getPlayer().gainMeso(-item.getPrice(), false);
                    }
                    notifyBotIfEquipBought(c, itemId);
                    c.sendPacket(PacketCreator.shopTransaction((byte) 0));
                } else {
                    c.sendPacket(PacketCreator.shopTransaction((byte) 3));
                }

            } else {
                c.sendPacket(PacketCreator.shopTransaction((byte) 2));
            }

        } else if (item.getPitch() > 0) {
            Integer amount = checkedCost(item.getPitch(), quantity);
            if (amount == null) {
                c.sendPacket(PacketCreator.shopTransaction((byte) 0x06));
                return;
            }

            if (c.getPlayer().getInventory(InventoryType.ETC).countById(ItemId.PERFECT_PITCH) >= amount) {
                if (InventoryManipulator.checkSpace(c, itemId, quantity, "")) {
                    if (!ItemConstants.isRechargeable(itemId)) {
                        InventoryManipulator.addById(c, itemId, quantity, "", -1);
                        InventoryManipulator.removeById(c, InventoryType.ETC, ItemId.PERFECT_PITCH, amount, false, false);
                    } else {
                        short slotMax = ii.getSlotMax(c, item.getItemId());
                        quantity = slotMax;
                        InventoryManipulator.addById(c, itemId, quantity, "", -1);
                        InventoryManipulator.removeById(c, InventoryType.ETC, ItemId.PERFECT_PITCH, amount, false, false);
                    }
                    notifyBotIfEquipBought(c, itemId);
                    c.sendPacket(PacketCreator.shopTransaction((byte) 0));
                } else {
                    c.sendPacket(PacketCreator.shopTransaction((byte) 3));
                }
            }

        }
    }

    static Integer checkedCost(int unitPrice, short quantity) {
        if (unitPrice <= 0 || quantity < 1) {
            return null;
        }
        long cost = (long) unitPrice * quantity;
        return cost <= Integer.MAX_VALUE ? (int) cost : null;
    }

    private static void notifyBotIfEquipBought(Client c, int itemId) {
        if (ItemConstants.getInventoryType(itemId) != InventoryType.EQUIP) return;
        Item bought = c.getPlayer().getInventory(InventoryType.EQUIP).findById(itemId);
        if (bought != null) {
            AgentOwnerItemNotificationService.notifyOwnerGainedItem(c.getPlayer(), bought);
        }
    }

    private static boolean canSell(Item item, short quantity) {
        if (item == null) { //Basic check
            return false;
        }

        short iQuant = item.getQuantity();
        if (iQuant == 0xFFFF) {
            iQuant = 1;
        } else if (iQuant < 0) {
            return false;
        }

        if (!ItemConstants.isRechargeable(item.getItemId())) {
            return iQuant != 0 && quantity <= iQuant;
        }

        return true;
    }

    private static short getSellingQuantity(Item item, short quantity) {
        if (ItemConstants.isRechargeable(item.getItemId())) {
            quantity = item.getQuantity();
            if (quantity == 0xFFFF) {
                quantity = 1;
            }
        }

        return quantity;
    }

    static boolean canReceiveSaleProceeds(client.Character player, int recvMesos) {
        return recvMesos <= 0 || player.canHoldMeso(recvMesos);
    }

    static void rejectSaleForMesoCapacity(Client c) {
        c.sendPacket(PacketCreator.shopTransaction((byte) 0x8));
        c.sendPacket(PacketCreator.serverNotice(1, "You cannot carry any more mesos."));
    }

    public void sell(Client c, InventoryType type, short slot, short quantity) {
        if (quantity == 0xFFFF || quantity == 0) {
            quantity = 1;
        } else if (quantity < 0) {
            return;
        }

        Item item = c.getPlayer().getInventory(type).getItem(slot);
        if (canSell(item, quantity)) {
            quantity = getSellingQuantity(item, quantity);
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            int recvMesos = ii.getPrice(item.getItemId(), quantity);
            if (!canReceiveSaleProceeds(c.getPlayer(), recvMesos)) {
                rejectSaleForMesoCapacity(c);
                return;
            }

            InventoryManipulator.removeFromSlot(c, type, (byte) slot, quantity, false);
            if (recvMesos > 0) {
                c.getPlayer().gainMeso(recvMesos, false);
            }
            c.sendPacket(PacketCreator.shopTransaction((byte) 0x8));
        } else {
            c.sendPacket(PacketCreator.shopTransaction((byte) 0x5));
        }
    }

    public void recharge(Client c, short slot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Item item = c.getPlayer().getInventory(InventoryType.USE).getItem(slot);
        if (item == null || !ItemConstants.isRechargeable(item.getItemId())) {
            return;
        }
        short slotMax = ii.getSlotMax(c, item.getItemId());
        if (item.getQuantity() < 0) {
            return;
        }
        if (item.getQuantity() < slotMax) {
            int price = (int) Math.ceil(ii.getUnitPrice(item.getItemId()) * (slotMax - item.getQuantity()));
            if (c.getPlayer().getMeso() >= price) {
                item.setQuantity(slotMax);
                c.getPlayer().forceUpdateItem(item);
                c.getPlayer().gainMeso(-price, false, true, false);
                c.sendPacket(PacketCreator.shopTransaction((byte) 0x8));
            } else {
                c.sendPacket(PacketCreator.shopTransaction((byte) 0x2));
            }
        }
    }

    private ShopItem findBySlot(short slot) {
        if (slot < 0 || slot >= items.size()) {
            return null;
        }
        return items.get(slot);
    }

    public static Shop createFromDB(int id, boolean isShopId) {
        Shop ret = null;
        int shopId = -1;
        try (Connection con = DatabaseConnection.getConnection()) {
            final String query;
            if (isShopId) {
                query = "SELECT * FROM shops WHERE shopid = ?";
            } else {
                query = "SELECT * FROM shops WHERE npcid = ?";
            }

            try (PreparedStatement ps = con.prepareStatement(query)) {
                ps.setInt(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        shopId = rs.getInt("shopid");
                        ret = new Shop(shopId, rs.getInt("npcid"));
                    } else {
                        return null;
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement("SELECT itemid, price, pitch FROM shopitems WHERE shopid = ? ORDER BY position DESC")) {
                ps.setInt(1, shopId);

                try (ResultSet rs = ps.executeQuery()) {
                    List<Integer> recharges = new ArrayList<>(rechargeableItems);
                    while (rs.next()) {
                        if (ItemConstants.isRechargeable(rs.getInt("itemid"))) {
                            ShopItem starItem = new ShopItem((short) 1, rs.getInt("itemid"), rs.getInt("price"), rs.getInt("pitch"));
                            ret.addItem(starItem);
                            if (rechargeableItems.contains(starItem.getItemId())) {
                                recharges.remove(Integer.valueOf(starItem.getItemId()));
                            }
                        } else {
                            ret.addItem(new ShopItem((short) 1000, rs.getInt("itemid"), rs.getInt("price"), rs.getInt("pitch")));
                        }
                    }
                    for (Integer recharge : recharges) {
                        ret.addItem(new ShopItem((short) 1000, recharge, 0, 0));
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to load shop requestId={} isShopId={} resolvedShopId={}", id, isShopId, shopId, e);
        }
        return ret;
    }

    public int getNpcId() {
        return npcId;
    }

    public int getId() {
        return id;
    }
}
