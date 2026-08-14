package server;

import client.Character;
import client.Client;
import client.SkillFactory;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ModifyInventory;
import client.inventory.manipulator.InventoryManipulator;
import constants.id.ItemId;
import constants.inventory.ItemConstants;
import server.agents.capabilities.social.AgentScrollReactionNotificationService;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.List;

/** One authoritative Cosmic scrolling mutation shared by packets and autonomous agents. */
public final class ScrollTransactionService {
    private ScrollTransactionService() { }

    public static Result apply(Client client, short scrollSlot, short equipSlot, byte flags) {
        boolean whiteScroll = (flags & 2) == 2;
        boolean legendarySpirit = false;
        ItemInformationProvider information = ItemInformationProvider.getInstance();
        Character character = client.getPlayer();
        Item equippedItem = character.getInventory(InventoryType.EQUIPPED).getItem(equipSlot);
        Equip toScroll = equippedItem instanceof Equip equip ? equip : null;
        if (character.getSkillLevel(SkillFactory.getSkill(1003)) > 0 && equipSlot >= 0) {
            legendarySpirit = true;
            Item inventoryItem = character.getInventory(InventoryType.EQUIP).getItem(equipSlot);
            toScroll = inventoryItem instanceof Equip equip ? equip : null;
        }
        Inventory useInventory = character.getInventory(InventoryType.USE);
        Item scroll = useInventory.getItem(scrollSlot);
        if (toScroll == null || scroll == null) return reject(client, legendarySpirit, "MISSING_ITEM");
        int scrollItemId = scroll.getItemId();
        int equipmentItemId = toScroll.getItemId();
        byte oldLevel = toScroll.getLevel();
        byte oldSlots = toScroll.getUpgradeSlots();
        Item whiteScrollItem = null;

        if (ItemConstants.isCleanSlate(scrollItemId) && !information.canUseCleanSlate(toScroll))
            return reject(client, legendarySpirit, "CLEAN_SLATE_INELIGIBLE");
        if (!ItemConstants.isModifierScroll(scrollItemId) && toScroll.getUpgradeSlots() < 1
                && !ItemConstants.isCleanSlate(scrollItemId))
            return reject(client, legendarySpirit, "NO_UPGRADE_SLOTS");
        List<Integer> requirements = information.getScrollReqs(scrollItemId);
        if (!requirements.isEmpty() && !requirements.contains(equipmentItemId))
            return reject(client, legendarySpirit, "WRONG_EQUIPMENT");
        if (whiteScroll) {
            whiteScrollItem = useInventory.findById(ItemId.WHITE_SCROLL);
            if (whiteScrollItem == null) whiteScroll = false;
        }
        if (!ItemConstants.isChaosScroll(scrollItemId) && !ItemConstants.isCleanSlate(scrollItemId)
                && !information.canApplyScroll(scrollItemId, equipmentItemId))
            return reject(client, legendarySpirit, "WRONG_EQUIPMENT");
        if (scroll.getQuantity() < 1) return reject(client, legendarySpirit, "SCROLL_DEPLETED");
        if (whiteScroll && !ItemConstants.isCleanSlate(scrollItemId)
                && (whiteScrollItem == null || whiteScrollItem.getQuantity() < 1))
            return reject(client, legendarySpirit, "WHITE_SCROLL_DEPLETED");

        Equip scrolled = (Equip) information.scrollEquipWithId(
                toScroll, scrollItemId, whiteScroll, 0, character.isGM());
        Equip.ScrollResult outcome = Equip.ScrollResult.FAIL;
        if (scrolled == null) outcome = Equip.ScrollResult.CURSE;
        else if (scrolled.getLevel() > oldLevel
                || (ItemConstants.isCleanSlate(scrollItemId) && scrolled.getUpgradeSlots() == oldSlots + 1)
                || ItemConstants.isFlagModifier(scrollItemId, scrolled.getFlag()))
            outcome = Equip.ScrollResult.SUCCESS;

        useInventory.lockInventory();
        try {
            if (whiteScroll && !ItemConstants.isCleanSlate(scrollItemId)) {
                InventoryManipulator.removeFromSlot(client, InventoryType.USE,
                        whiteScrollItem.getPosition(), (short) 1, false, false);
            }
            InventoryManipulator.removeFromSlot(client, InventoryType.USE,
                    scroll.getPosition(), (short) 1, false);
        } finally {
            useInventory.unlockInventory();
        }

        List<ModifyInventory> modifications = new ArrayList<>();
        if (outcome == Equip.ScrollResult.CURSE) {
            if (!ItemId.isWeddingRing(toScroll.getItemId())) {
                modifications.add(new ModifyInventory(3, toScroll));
                Inventory inventory = character.getInventory(
                        equipSlot < 0 ? InventoryType.EQUIPPED : InventoryType.EQUIP);
                inventory.lockInventory();
                try {
                    if (equipSlot < 0) character.unequippedItem(toScroll);
                    inventory.removeItem(toScroll.getPosition());
                } finally {
                    inventory.unlockInventory();
                }
            } else {
                scrolled = toScroll;
                outcome = Equip.ScrollResult.FAIL;
                modifications.add(new ModifyInventory(3, scrolled));
                modifications.add(new ModifyInventory(0, scrolled));
            }
        } else {
            modifications.add(new ModifyInventory(3, scrolled));
            modifications.add(new ModifyInventory(0, scrolled));
        }
        client.sendPacket(PacketCreator.modifyInventory(true, modifications));
        character.getMap().broadcastMessage(PacketCreator.getScrollEffect(
                character.getId(), outcome, legendarySpirit, whiteScroll));
        AgentScrollReactionNotificationService.notifyNearbyAgentsOfScroll(
                character, outcome, scrollItemId, 3_000L);
        if (equipSlot < 0 && (outcome == Equip.ScrollResult.SUCCESS || outcome == Equip.ScrollResult.CURSE))
            character.equipChanged();
        return new Result(true, outcome.name(), scrollItemId, equipmentItemId, whiteScroll);
    }

    private static Result reject(Client client, boolean legendarySpirit, String reason) {
        if (legendarySpirit)
            client.sendPacket(PacketCreator.getScrollEffect(
                    client.getPlayer().getId(), Equip.ScrollResult.FAIL, false, false));
        else client.sendPacket(PacketCreator.getInventoryFull());
        return new Result(false, reason, 0, 0, false);
    }

    public record Result(boolean applied, String outcome, int scrollItemId,
                         int equipmentItemId, boolean whiteScroll) { }
}
