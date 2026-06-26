package server.agents.capabilities.dialogue;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryDialogueReporterTest {
    @Test
    void shouldCountEquipScrollsInUseInventoryLikeLegacyChat() {
        Character agent = mock(Character.class);
        Inventory use = mock(Inventory.class);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.list()).thenReturn(List.of(
                new Item(2040000, (short) 1, (short) 2),
                new Item(2000000, (short) 2, (short) 9),
                new Item(2040501, (short) 3, (short) 3)));

        assertEquals(5, AgentInventoryDialogueReporter.countEquipScrolls(agent));
        assertEquals("I have 5 scrolls on me", AgentInventoryDialogueReporter.scrollReport(agent));
    }

    @Test
    void shouldReportNoScrollsWhenUseInventoryHasNoEquipScrolls() {
        Character agent = mock(Character.class);
        Inventory use = mock(Inventory.class);
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.list()).thenReturn(List.of(new Item(2000000, (short) 1, (short) 9)));

        assertEquals(0, AgentInventoryDialogueReporter.countEquipScrolls(agent));
        assertEquals("no scrolls on me", AgentInventoryDialogueReporter.scrollReport(agent));
    }

    @Test
    void shouldBuildSlotReportInLegacyInventoryOrder() {
        Character agent = mock(Character.class);
        stubInventory(agent, InventoryType.EQUIP, 24, 14);
        stubInventory(agent, InventoryType.USE, 28, 20);
        stubInventory(agent, InventoryType.ETC, 32, 29);
        stubInventory(agent, InventoryType.SETUP, 36, 36);

        assertEquals("equip: 10/24, use: 8/28, etc: 3/32, setup: 0/36",
                AgentInventoryDialogueReporter.slotsReport(agent));
    }

    @Test
    void shouldResolveNoItemsCategoryLabelsLikeLegacyInventoryChat() {
        assertEquals("mesos", AgentInventoryDialogueReporter.noItemsCategoryLabel("mesos"));
        assertEquals("better gear for you", AgentInventoryDialogueReporter.noItemsCategoryLabel("recommended"));
        assertEquals("scrolls", AgentInventoryDialogueReporter.noItemsCategoryLabel("scrolls"));
        assertEquals("pots", AgentInventoryDialogueReporter.noItemsCategoryLabel("pots"));
        assertEquals("buff pots", AgentInventoryDialogueReporter.noItemsCategoryLabel("buff"));
        assertEquals("use items", AgentInventoryDialogueReporter.noItemsCategoryLabel("use"));
        assertEquals("ammo", AgentInventoryDialogueReporter.noItemsCategoryLabel("ammo"));
        assertEquals("equips", AgentInventoryDialogueReporter.noItemsCategoryLabel("equips"));
        assertEquals("trash equips", AgentInventoryDialogueReporter.noItemsCategoryLabel("trash"));
        assertEquals("etc items", AgentInventoryDialogueReporter.noItemsCategoryLabel("etc"));
        assertEquals("reserved equips", AgentInventoryDialogueReporter.noItemsCategoryLabel("equips:reserved:2"));
        assertEquals("mesos", AgentInventoryDialogueReporter.noItemsCategoryLabel("mesos:1000"));
        assertEquals("slime bubble", AgentInventoryDialogueReporter.noItemsCategoryLabel("name:slime bubble"));
        assertEquals("those items", AgentInventoryDialogueReporter.noItemsCategoryLabel("unknown"));
        assertEquals("those items", AgentInventoryDialogueReporter.noItemsCategoryLabel(null));
    }

    @Test
    void shouldFormatNoItemsReplyWithProvidedTemplate() {
        assertEquals("no pots on me rn",
                AgentInventoryDialogueReporter.noItemsReply("pots", List.of("no %s on me rn")));
        assertEquals("checked, no reserved equips",
                AgentInventoryDialogueReporter.noItemsReply("equips:reserved:3", List.of("checked, no %s")));
    }

    private static void stubInventory(Character agent, InventoryType type, int totalSlots, int freeSlots) {
        Inventory inventory = mock(Inventory.class);
        when(agent.getInventory(type)).thenReturn(inventory);
        when(inventory.getSlotLimit()).thenReturn((byte) totalSlots);
        when(inventory.getNumFreeSlot()).thenReturn((short) freeSlots);
    }
}
