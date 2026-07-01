package server.agents.capabilities.inventory;

import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeClassification;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipTradeGroupServiceTest {
    @Test
    void allTradeItemsKeepLegacyGroupOrder() {
        Item normal = item(1000);
        Item other = item(1001);
        Item self = item(1002);
        AgentEquipTradeGroups groups = new AgentEquipTradeGroups(List.of(normal), List.of(other), List.of(self));

        assertEquals(List.of(normal, other, self), AgentEquipTradeGroupService.allTradeItems(groups));
    }

    @Test
    void reservedEquipsReturnsSelfReservedCopy() {
        Item self = item(1002);
        AgentEquipTradeGroups groups = new AgentEquipTradeGroups(List.of(), List.of(), List.of(self));

        List<Item> reserved = AgentEquipTradeGroupService.reservedEquips(groups);

        assertEquals(List.of(self), reserved);
        reserved.clear();
        assertEquals(List.of(self), groups.reservedForSelf());
    }

    @Test
    void reservedEquipPageDelegatesLegacyPagePolicy() {
        Item first = item(1000);
        Item tenth = item(1009);
        AgentEquipTradeGroups groups = new AgentEquipTradeGroups(List.of(), List.of(),
                List.of(first, item(1001), item(1002), item(1003), item(1004),
                        item(1005), item(1006), item(1007), item(1008), tenth));

        assertEquals(List.of(tenth), AgentEquipTradeGroupService.reservedEquipTradePage("equips:reserved:2", groups));
        assertEquals("reserved equips page 2/2",
                AgentEquipTradeGroupService.reservedEquipsPageMessage("equips:reserved:2", groups));
    }

    @Test
    void groupMessageUsesLegacyReservedBucketsOnly() {
        assertEquals("other", AgentEquipTradeGroupService.equipsGroupMessage(
                "equips:reserved_for_other", () -> "other", () -> "self"));
        assertEquals("self", AgentEquipTradeGroupService.equipsGroupMessage(
                "equips:reserved_for_self", () -> "other", () -> "self"));
        assertNull(AgentEquipTradeGroupService.equipsGroupMessage(
                "equips:normal", () -> "other", () -> "self"));
        assertNull(AgentEquipTradeGroupService.equipsGroupMessage(
                "equips:nope", () -> "other", () -> "self"));
    }

    @Test
    void selectsFirstAndNextAvailableEquipGroupLikeLegacyInventory() {
        AgentEquipTradeGroups groups = new AgentEquipTradeGroups(List.of(), List.of(item(1001)), List.of(item(1002)));

        assertEquals(EquipsGroup.RESERVED_FOR_OTHER, AgentEquipTradeGroupService.firstAvailableGroup(groups));
        assertEquals("equips:reserved_for_self",
                AgentEquipTradeGroupService.nextEquipsGroup("equips:reserved_for_other", groups));
        assertNull(AgentEquipTradeGroupService.nextEquipsGroup("equips:reserved_for_self", groups));
    }

    @Test
    void classifiesSelfKeepBeforeOtherReservationsAndSortsBuckets() {
        Item normalHigh = item(3000, (short) 3);
        Item normalLow = item(1000, (short) 1);
        Item reservedOther = item(2000, (short) 2);
        Item reservedSelf = item(4000, (short) 4);

        AgentEquipTradeClassification classification = AgentEquipTradeGroupService.classifyEquipGroups(
                mock(client.Character.class),
                List.of(normalHigh, reservedSelf, reservedOther, normalLow),
                Set.of(reservedSelf),
                item -> item == reservedOther || item == reservedSelf,
                true);

        assertEquals(List.of(normalLow, normalHigh), classification.groups().normal());
        assertEquals(List.of(reservedOther), classification.groups().reservedForOther());
        assertEquals(List.of(reservedSelf), classification.groups().reservedForSelf());
        assertEquals(3, classification.reservedOtherChecks());
        assertEquals(1, classification.reservedOtherHits());
    }

    private static Item item(int itemId) {
        return item(itemId, (short) itemId);
    }

    private static Item item(int itemId, short position) {
        Item item = mock(Item.class);
        when(item.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getPosition()).thenReturn(position);
        return item;
    }
}
