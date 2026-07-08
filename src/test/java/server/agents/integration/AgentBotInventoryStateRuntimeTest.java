package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotInventoryStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotInventoryStateRuntimeTest {
    @Test
    void adaptsLootInhibitCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotInventoryStateRuntime.hasLootInhibit(entry));

        AgentBotInventoryStateRuntime.setLootInhibitMs(entry, 1_000);

        assertTrue(AgentBotInventoryStateRuntime.hasLootInhibit(entry));
        assertEquals(1_000, AgentBotInventoryStateRuntime.lootInhibitMs(entry));

        AgentBotInventoryStateRuntime.tickLootInhibit(entry, value -> value - 250);

        assertEquals(750, AgentBotInventoryStateRuntime.lootInhibitMs(entry));
    }

    @Test
    void adaptsInventoryFullWarningCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentBotInventoryStateRuntime.canWarnInventoryFull(entry));

        AgentBotInventoryStateRuntime.setInventoryFullWarnCooldownMs(entry, 5_000);

        assertFalse(AgentBotInventoryStateRuntime.canWarnInventoryFull(entry));
        assertEquals(5_000, AgentBotInventoryStateRuntime.inventoryFullWarnCooldownMs(entry));

        AgentBotInventoryStateRuntime.tickInventoryFullWarnCooldown(entry, value -> value - 500);

        assertEquals(4_500, AgentBotInventoryStateRuntime.inventoryFullWarnCooldownMs(entry));
    }
}
