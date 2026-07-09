package server.agents.capabilities.dialogue;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.integration.InventoryGateway;
import tools.Pair;

import java.util.List;

import static client.BuffStat.WATK;
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
    void shouldBuildInventorySummaryInLegacyInventoryOrder() {
        Character agent = mock(Character.class);
        stubInventory(agent, InventoryType.EQUIP, 24, 14);
        stubInventory(agent, InventoryType.USE, 28, 20);
        stubInventory(agent, InventoryType.ETC, 32, 29);
        stubInventory(agent, InventoryType.SETUP, 36, 36);

        assertEquals("equip 10/24 | use 8/28 | etc 3/32 | setup 0/36",
                AgentInventoryDialogueReporter.inventorySummary(agent, inventoryGateway()));
    }

    @Test
    void shouldSummarizeSafeUseInventoryItemsThroughGateway() {
        Character agent = mock(Character.class);
        stubInventory(agent, InventoryType.EQUIP, 24, 24);
        Inventory use = stubInventory(agent, InventoryType.USE, 28, 23);
        stubInventory(agent, InventoryType.ETC, 32, 32);
        stubInventory(agent, InventoryType.SETUP, 36, 36);
        when(use.list()).thenReturn(List.of(
                new Item(2040000, (short) 1, (short) 2),
                new Item(2000000, (short) 2, (short) 3),
                new Item(2022179, (short) 3, (short) 4),
                new Item(4000000, (short) 4, (short) 99)));
        InventoryGateway inventoryGateway = inventoryGateway();
        StatEffect pot = mock(StatEffect.class);
        when(pot.getHp()).thenReturn((short) 50);
        when(pot.getStatups()).thenReturn(List.of());
        StatEffect buff = mock(StatEffect.class);
        when(buff.getStatups()).thenReturn(List.of(new Pair<>(WATK, 12)));
        when(inventoryGateway.getItemEffect(2000000)).thenReturn(pot);
        when(inventoryGateway.getItemEffect(2022179)).thenReturn(buff);
        when(inventoryGateway.isQuestItem(4000000)).thenReturn(true);

        assertEquals("equip 0/24 | use 5/28 (2 scrolls, 3 pots, 4 buffs) | etc 0/32 | setup 0/36",
                AgentInventoryDialogueReporter.inventorySummary(agent, inventoryGateway));
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

    private static Inventory stubInventory(Character agent, InventoryType type, int totalSlots, int freeSlots) {
        Inventory inventory = mock(Inventory.class);
        when(agent.getInventory(type)).thenReturn(inventory);
        when(inventory.getSlotLimit()).thenReturn((byte) totalSlots);
        when(inventory.getNumFreeSlot()).thenReturn((short) freeSlots);
        when(inventory.list()).thenReturn(List.of());
        return inventory;
    }

    private static InventoryGateway inventoryGateway() {
        return mock(InventoryGateway.class);
    }
}
