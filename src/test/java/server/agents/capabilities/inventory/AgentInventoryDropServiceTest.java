package server.agents.capabilities.inventory;

import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentBotInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.IntPredicate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentInventoryDropServiceTest {
    @Test
    void dropEtcDropsSafeMatchingItemsAndRepliesWithLegacyText() {
        Character agent = mock(Character.class);
        Client client = mock(Client.class);
        Inventory etc = mock(Inventory.class);
        Item item = mock(Item.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        IntPredicate oldQuestItemLookup = AgentInventoryDropService.questItemLookup;

        when(agent.getClient()).thenReturn(client);
        when(agent.getInventory(InventoryType.ETC)).thenReturn(etc);
        when(etc.getSlotLimit()).thenReturn((byte) 1);
        when(etc.getItem((short) 1)).thenReturn(item);
        when(item.getItemId()).thenReturn(4000000);
        when(item.getQuantity()).thenReturn((short) 2);
        when(item.isUntradeable()).thenReturn(false);
        AgentInventoryDropService.questItemLookup = itemId -> false;

        try (MockedStatic<InventoryManipulator> inventory = mockStatic(InventoryManipulator.class);
             MockedStatic<AgentBotInventoryRuntime> runtime = mockStatic(AgentBotInventoryRuntime.class)) {
            AgentInventoryDropService.dropCategory("etc", entry, agent, (ignoredEntry, ignoredAgent) -> List.of());

            inventory.verify(() -> InventoryManipulator.drop(client, InventoryType.ETC, (short) 1, (short) 2));
            runtime.verify(() -> AgentBotInventoryRuntime.replyNow(entry, "dropped 1 etc item!"));
        } finally {
            AgentInventoryDropService.questItemLookup = oldQuestItemLookup;
        }
    }

    @Test
    void dropByNameRepliesWhenNoMatchingItemExists() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        for (InventoryType type : List.of(InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            Inventory inventory = mock(Inventory.class);
            when(inventory.getSlotLimit()).thenReturn((byte) 0);
            when(agent.getInventory(type)).thenReturn(inventory);
        }

        try (MockedStatic<AgentBotInventoryRuntime> runtime = mockStatic(AgentBotInventoryRuntime.class)) {
            AgentInventoryDropService.dropCategory("name:red potion", entry, agent, (ignoredEntry, ignoredAgent) -> List.of());

            runtime.verify(() -> AgentBotInventoryRuntime.replyNow(
                    entry,
                    AgentDialogueCatalog.tradeNamedItemNotFoundReply("red potion")));
        }
    }
}
