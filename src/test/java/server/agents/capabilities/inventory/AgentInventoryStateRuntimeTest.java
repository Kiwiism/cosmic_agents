package server.agents.capabilities.inventory;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentInventoryStateRuntimeTest {
    @Test
    void adaptsLootInhibitCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentInventoryStateRuntime.hasLootInhibit(entry));

        AgentInventoryStateRuntime.setLootInhibitMs(entry, 1_000);

        assertTrue(AgentInventoryStateRuntime.hasLootInhibit(entry));
        assertEquals(1_000, AgentInventoryStateRuntime.lootInhibitMs(entry));

        AgentInventoryStateRuntime.tickLootInhibit(entry, value -> value - 250);

        assertEquals(750, AgentInventoryStateRuntime.lootInhibitMs(entry));
    }

    @Test
    void adaptsInventoryFullWarningCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentInventoryStateRuntime.canWarnInventoryFull(entry));

        AgentInventoryStateRuntime.setInventoryFullWarnCooldownMs(entry, 5_000);

        assertFalse(AgentInventoryStateRuntime.canWarnInventoryFull(entry));
        assertEquals(5_000, AgentInventoryStateRuntime.inventoryFullWarnCooldownMs(entry));

        AgentInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, value -> value - 500);

        assertEquals(4_500, AgentInventoryStateRuntime.inventoryFullWarnCooldownMs(entry));
    }
}
