package server.agents.capabilities.inventory;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryReservationRuntimeTest {
    @Test
    void oneReservationViewProtectsItemsFromEveryConsumer() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Item questItem = item(4000000);
        Item spareItem = item(4000001);
        AgentInventoryReservationRuntime.reserveObjectiveItems(
                entry, Map.of(4000000, 3), AgentInventoryReservationRuntime.LOOT_CAPABILITY,
                AgentDisposition.QUEST_RESERVE, "active quest", 900, 100L);

        assertFalse(AgentInventoryReservationRuntime.mayConsume(entry, questItem, 101L));
        assertTrue(AgentInventoryReservationRuntime.mayConsume(entry, spareItem, 101L));
        assertEquals(List.of(spareItem), AgentInventoryReservationRuntime.unreservedItems(
                entry, List.of(questItem, spareItem), 101L));

        AgentInventoryReservationRuntime.releaseCapability(
                entry, AgentInventoryReservationRuntime.LOOT_CAPABILITY);
        assertTrue(AgentInventoryReservationRuntime.mayConsume(entry, questItem, 102L));
    }

    private static Item item(int itemId) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        return item;
    }
}
