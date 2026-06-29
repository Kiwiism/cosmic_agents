package server.agents.capabilities.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
