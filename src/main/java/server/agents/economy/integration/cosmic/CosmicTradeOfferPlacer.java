package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import client.inventory.manipulator.KarmaManipulator;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import server.ItemRestrictionPolicy;
import server.Trade;
import server.economy.EconomyItemEvidence;
import tools.PacketCreator;

/** Places server-controlled trade items through the same restrictions as a client packet. */
final class CosmicTradeOfferPlacer {
    private CosmicTradeOfferPlacer() { }

    static Item findExact(Character seller, InventoryType type, short slot, int itemId,
                          String fingerprint, int quantity) {
        if (seller == null || type == null || slot <= 0 || itemId <= 0 || quantity <= 0
                || quantity > Short.MAX_VALUE || fingerprint == null || fingerprint.isBlank()) return null;
        Item item = seller.getInventory(type).getItem(slot);
        return item != null && item.getItemId() == itemId && item.getQuantity() >= quantity
                && EconomyItemEvidence.describe(item).fingerprint().equals(fingerprint) ? item : null;
    }

    static boolean placeExact(Character seller, InventoryType type, short slot, int itemId,
                              String fingerprint, int quantity, byte targetSlot) {
        if (seller == null || seller.getClient() == null || seller.getTrade() == null
                || targetSlot < 1 || targetSlot > 9) return false;
        Inventory inventory = seller.getInventory(type);
        inventory.lockInventory();
        try {
            Item item = findExact(seller, type, slot, itemId, fingerprint, quantity);
            if (item == null || !tradeable(seller, item)) return false;
            Item tradeItem = item.copy();
            tradeItem.setQuantity((short) quantity);
            tradeItem.setPosition(targetSlot);
            Trade trade = seller.getTrade();
            if (!trade.addItem(tradeItem)) return false;
            InventoryManipulator.removeFromSlot(seller.getClient(), type, slot, (short) quantity, true);
            seller.sendPacket(PacketCreator.getTradeItemAdd((byte) 0, tradeItem));
            if (trade.getPartner() != null)
                trade.getPartner().getChr().sendPacket(PacketCreator.getTradeItemAdd((byte) 1, tradeItem));
            return true;
        } finally {
            inventory.unlockInventory();
        }
    }

    private static boolean tradeable(Character seller, Item item) {
        ItemInformationProvider information = ItemInformationProvider.getInstance();
        if (information.isUnmerchable(item.getItemId()) || ItemConstants.isRechargeable(item.getItemId()))
            return false;
        return !information.isDropRestricted(item.getItemId())
                || ItemRestrictionPolicy.allowsUntradeable(seller, item.getItemId())
                || KarmaManipulator.hasKarmaFlag(item);
    }
}
