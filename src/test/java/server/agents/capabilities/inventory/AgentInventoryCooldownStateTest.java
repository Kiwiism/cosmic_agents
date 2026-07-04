package server.agents.capabilities.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentInventoryCooldownStateTest {
    @Test
    void storesLootAndInventoryWarningCooldowns() {
        AgentInventoryCooldownState state = new AgentInventoryCooldownState();

        assertEquals(0, state.lootInhibitMs());
        assertEquals(0, state.inventoryFullWarnCooldownMs());

        state.setLootInhibitMs(1_000);
        state.setInventoryFullWarnCooldownMs(5_000);

        assertEquals(1_000, state.lootInhibitMs());
        assertEquals(5_000, state.inventoryFullWarnCooldownMs());
    }
}
