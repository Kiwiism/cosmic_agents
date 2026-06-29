package server.agents.capabilities.inventory;

import client.inventory.InventoryType;
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

    private static Item item(InventoryType type, int quantity) {
        Item item = mock(Item.class);
        when(item.getInventoryType()).thenReturn(type);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
