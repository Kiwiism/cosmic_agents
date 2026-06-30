package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Inventory;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryTradePolicyTest {
    @Test
    void shouldBuildReservedEquipsCategoryLikeLegacyInventory() {
        assertEquals("equips:reserved:3", AgentInventoryTradePolicy.reservedEquipsCategory(3));
        assertTrue(AgentInventoryTradePolicy.isReservedEquipsCategory("equips:reserved:2"));
        assertFalse(AgentInventoryTradePolicy.isReservedEquipsCategory("equips:reserved"));
        assertFalse(AgentInventoryTradePolicy.isReservedEquipsCategory(null));
        assertEquals(2, AgentInventoryTradePolicy.requestedReservedEquipsPage("equips:reserved:2"));
        assertEquals(1, AgentInventoryTradePolicy.requestedReservedEquipsPage("equips:reserved:nope"));
        assertEquals(1, AgentInventoryTradePolicy.requestedReservedEquipsPage("equips:normal"));
    }

    @Test
    void shouldClampTradePagesLikeLegacyInventory() {
        assertEquals(1, AgentInventoryTradePolicy.clampTradePage(-4, 0));
        assertEquals(1, AgentInventoryTradePolicy.clampTradePage(0, 5));
        assertEquals(1, AgentInventoryTradePolicy.clampTradePage(1, 9));
        assertEquals(2, AgentInventoryTradePolicy.clampTradePage(2, 10));
        assertEquals(2, AgentInventoryTradePolicy.clampTradePage(99, 10));
    }

    @Test
    void shouldBuildReservedEquipPageMessageLikeLegacyInventory() {
        assertEquals(null, AgentInventoryTradePolicy.reservedEquipsPageMessage("equips:reserved:1", 0));
        assertEquals("reserved equips page 1/1",
                AgentInventoryTradePolicy.reservedEquipsPageMessage("equips:reserved:bad", 4));
        assertEquals("reserved equips page 2/2",
                AgentInventoryTradePolicy.reservedEquipsPageMessage("equips:reserved:99", 10));
    }

    @Test
    void shouldSelectReservedEquipPageItemsLikeLegacyInventory() {
        List<Item> reserved = List.of(
                item(InventoryType.EQUIP, 1000, 1),
                item(InventoryType.EQUIP, 1001, 1),
                item(InventoryType.EQUIP, 1002, 1),
                item(InventoryType.EQUIP, 1003, 1),
                item(InventoryType.EQUIP, 1004, 1),
                item(InventoryType.EQUIP, 1005, 1),
                item(InventoryType.EQUIP, 1006, 1),
                item(InventoryType.EQUIP, 1007, 1),
                item(InventoryType.EQUIP, 1008, 1),
                item(InventoryType.EQUIP, 1009, 1));

        assertEquals(List.of(), AgentInventoryTradePolicy.reservedEquipsPageItems("equips:reserved:1", List.of()));
        assertEquals(reserved.subList(0, 9),
                AgentInventoryTradePolicy.reservedEquipsPageItems("equips:reserved:1", reserved));
        assertEquals(reserved.subList(9, 10),
                AgentInventoryTradePolicy.reservedEquipsPageItems("equips:reserved:2", reserved));
        assertEquals(reserved.subList(9, 10),
                AgentInventoryTradePolicy.reservedEquipsPageItems("equips:reserved:99", reserved));
    }

    @Test
    void shouldParseMesoTradeCategoriesLikeLegacyInventory() {
        assertTrue(AgentInventoryTradePolicy.isMesoCategory("mesos"));
        assertTrue(AgentInventoryTradePolicy.isMesoCategory("mesos:1500"));
        assertFalse(AgentInventoryTradePolicy.isMesoCategory(null));
        assertFalse(AgentInventoryTradePolicy.isMesoCategory("items"));

        assertEquals(-1, AgentInventoryTradePolicy.requestedTradeMesos("mesos"));
        assertEquals(1500, AgentInventoryTradePolicy.requestedTradeMesos("mesos:1500"));
        assertEquals(0, AgentInventoryTradePolicy.requestedTradeMesos("mesos:nope"));
        assertEquals(0, AgentInventoryTradePolicy.requestedTradeMesos("items"));
    }

    @Test
    void shouldParseEquipAndAmmoGroupCategoriesLikeLegacyInventory() {
        assertEquals(AgentInventoryTradePolicy.EquipsGroup.NORMAL,
                AgentInventoryTradePolicy.equipsGroupFromCategory("equips:normal"));
        assertEquals(AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_SELF,
                AgentInventoryTradePolicy.equipsGroupFromCategory("equips:reserved_for_self"));
        assertEquals("equips:reserved_for_other",
                AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_OTHER.categoryString());
        assertEquals(AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_SELF,
                AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_OTHER.next());
        assertEquals(AgentInventoryTradePolicy.AmmoGroup.NON_OWN,
                AgentInventoryTradePolicy.ammoGroupFromCategory("ammo:non_own"));
        assertEquals(AgentInventoryTradePolicy.AmmoGroup.OWN,
                AgentInventoryTradePolicy.AmmoGroup.NON_OWN.next());
        assertEquals("ammo:own", AgentInventoryTradePolicy.AmmoGroup.OWN.categoryString());
        assertEquals(null, AgentInventoryTradePolicy.equipsGroupFromCategory("equips:reserved:1"));
        assertEquals(null, AgentInventoryTradePolicy.ammoGroupFromCategory("ammo"));
    }

    @Test
    void shouldSelectEquipTradeGroupsLikeLegacyInventory() {
        assertEquals(AgentInventoryTradePolicy.EquipsGroup.NORMAL,
                AgentInventoryTradePolicy.firstAvailableEquipsGroup(group -> true));
        assertEquals(AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_SELF,
                AgentInventoryTradePolicy.firstAvailableEquipsGroup(
                        group -> group == AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_SELF));
        assertEquals(null, AgentInventoryTradePolicy.firstAvailableEquipsGroup(group -> false));
        assertEquals("equips:reserved_for_self",
                AgentInventoryTradePolicy.nextAvailableEquipsGroupCategory("equips:reserved_for_other",
                        group -> group == AgentInventoryTradePolicy.EquipsGroup.RESERVED_FOR_SELF));
        assertEquals(null,
                AgentInventoryTradePolicy.nextAvailableEquipsGroupCategory("equips:reserved_for_self", group -> true));
        assertEquals(null,
                AgentInventoryTradePolicy.nextAvailableEquipsGroupCategory("equips:nope", group -> true));
    }

    @Test
    void shouldBuildNotEnoughMesosReplyLikeLegacyInventory() {
        assertEquals("i only have 1,000 mesos rn, not 50,000",
                AgentInventoryTradePolicy.notEnoughMesosReply(50_000, 1_000));
    }

    @Test
    void shouldSumEquipAsOneAndStackItemsByPositiveQuantity() {
        assertEquals(8, AgentInventoryTradePolicy.itemQuantitySum(List.of(
                item(InventoryType.EQUIP, 99),
                item(InventoryType.USE, 5),
                item(InventoryType.ETC, 2),
                item(InventoryType.SETUP, -4))));
    }

    @Test
    void shouldPrioritizeEtcItemsAlreadyOwnedByRecipientAfterItemIdSort() {
        Item highIdDuplicate = item(InventoryType.ETC, 3000, 1);
        Item lowIdRemainder = item(InventoryType.ETC, 1000, 1);
        Item midIdDuplicate = item(InventoryType.ETC, 2000, 1);
        Character recipient = mock(Character.class);
        Inventory recipientEtc = inventoryWith(item(InventoryType.ETC, 2000, 1), item(InventoryType.ETC, 3000, 1));
        when(recipient.getInventory(InventoryType.ETC)).thenReturn(recipientEtc);

        List<Item> prioritized = AgentInventoryTradePolicy.prioritizeEtcTradeItems(
                List.of(highIdDuplicate, lowIdRemainder, midIdDuplicate),
                recipient);

        assertEquals(List.of(midIdDuplicate, highIdDuplicate, lowIdRemainder), prioritized);
    }

    @Test
    void shouldPrioritizeUseBucketsIndependentlyByRecipientDuplicateIds() {
        Item uncategorized = item(InventoryType.USE, 3000, 1);
        Item uncategorizedDuplicate = item(InventoryType.USE, 2000, 1);
        Item categorized = item(InventoryType.USE, 5000, 1);
        Item potionAmmoDuplicate = item(InventoryType.USE, 1000, 1);
        Character recipient = mock(Character.class);
        Inventory recipientUse = inventoryWith(item(InventoryType.USE, 1000, 1), item(InventoryType.USE, 2000, 1));
        when(recipient.getInventory(InventoryType.USE)).thenReturn(recipientUse);

        List<Item> prioritized = AgentInventoryTradePolicy.prioritizeTradeUseItems(
                List.of(uncategorized, uncategorizedDuplicate),
                List.of(categorized),
                List.of(potionAmmoDuplicate),
                recipient);

        assertEquals(List.of(uncategorizedDuplicate, uncategorized, categorized, potionAmmoDuplicate), prioritized);
    }

    @Test
    void shouldClassifyUseTradeGroupsLikeLegacyInventory() {
        Item uncategorized = item(InventoryType.USE, 3000, 1);
        Item scroll = item(InventoryType.USE, 2040000, 1);
        Item potion = item(InventoryType.USE, 2000000, 5);
        Item ammo = item(InventoryType.USE, 2060000, 50);
        Item questItem = item(InventoryType.USE, 4000000, 1);
        Character agent = mock(Character.class);
        Inventory useInventory = slottedInventory(uncategorized, scroll, potion, ammo, questItem);
        when(agent.getInventory(InventoryType.USE)).thenReturn(useInventory);

        AgentInventoryTradePolicy.UseTradeGroups groups = AgentInventoryTradePolicy.classifyUseTradeGroups(
                agent,
                null,
                id -> id == 2000000,
                id -> id == 2060000,
                id -> id == 2040000,
                id -> false,
                id -> id == 4000000,
                false);

        assertEquals(List.of(uncategorized), groups.uncategorized());
        assertEquals(List.of(scroll, potion, ammo), groups.categorized());
    }

    private static Item item(InventoryType type, int quantity) {
        return item(type, 0, quantity);
    }

    private static Item item(InventoryType type, int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getInventoryType()).thenReturn(type);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }

    private static Inventory inventoryWith(Item... items) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.iterator()).thenReturn(List.of(items).iterator());
        return inventory;
    }

    private static Inventory slottedInventory(Item... items) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSlotLimit()).thenReturn((byte) items.length);
        for (short slot = 1; slot <= items.length; slot++) {
            when(inventory.getItem(slot)).thenReturn(items[slot - 1]);
        }
        return inventory;
    }
}
