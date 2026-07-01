package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryTradeCollectionServiceTest {
    @Test
    void prepareNameItemsUsesEquippedSlotResultBeforeBagSearch() {
        Item equipped = item(1000, (short) 1);
        AtomicReference<String> fragmentSeen = new AtomicReference<>();

        PreparedTradeItems prepared = AgentInventoryTradeCollectionService.prepareTradeItems(
                "name: top ",
                mock(Character.class),
                fragment -> {
                    fragmentSeen.set(fragment);
                    return new PreparedTradeItems(List.of(equipped), null);
                },
                fragment -> List.of(item(2000, (short) 2)),
                List::of,
                () -> groups(List.of(), List.of(), List.of()),
                () -> new AmmoTradeGroups(List.of(), List.of()),
                mock(Character.class));

        assertEquals("top", fragmentSeen.get());
        assertEquals(List.of(equipped), prepared.items());
        assertEquals(null, prepared.errorMessage());
    }

    @Test
    void prepareNameItemsFallsBackToNamedBagItemsWhenNoEquippedSlotMatch() {
        Item named = item(2000, (short) 2);

        PreparedTradeItems prepared = AgentInventoryTradeCollectionService.prepareTradeItems(
                "name: glove ",
                mock(Character.class),
                fragment -> new PreparedTradeItems(List.of(), null),
                fragment -> List.of(named),
                List::of,
                () -> groups(List.of(), List.of(), List.of()),
                () -> new AmmoTradeGroups(List.of(), List.of()),
                mock(Character.class));

        assertEquals(List.of(named), prepared.items());
        assertEquals(null, prepared.errorMessage());
    }

    @Test
    void recommendedItemsRequireOwnerLikeLegacyInventory() {
        Item recommended = item(3000, (short) 3);
        AtomicBoolean called = new AtomicBoolean(false);

        List<Item> withoutOwner = AgentInventoryTradeCollectionService.collectItems(
                "recommended",
                mock(Character.class),
                null,
                () -> {
                    called.set(true);
                    return List.of(recommended);
                },
                () -> groups(List.of(), List.of(), List.of()),
                () -> new AmmoTradeGroups(List.of(), List.of()));

        assertEquals(List.of(), withoutOwner);
        assertFalse(called.get());

        List<Item> withOwner = AgentInventoryTradeCollectionService.collectItems(
                "recommended",
                mock(Character.class),
                mock(Character.class),
                () -> List.of(recommended),
                () -> groups(List.of(), List.of(), List.of()),
                () -> new AmmoTradeGroups(List.of(), List.of()));

        assertEquals(List.of(recommended), withOwner);
    }

    @Test
    void equipCategoriesUseAgentEquipGroupsInLegacyOrder() {
        Item normal = item(1000, (short) 1);
        Item other = item(1001, (short) 2);
        Item self = item(1002, (short) 3);

        List<Item> equips = AgentInventoryTradeCollectionService.collectItems(
                "equips",
                mock(Character.class),
                mock(Character.class),
                List::of,
                () -> groups(List.of(normal), List.of(other), List.of(self)),
                () -> new AmmoTradeGroups(List.of(), List.of()));
        List<Item> trash = AgentInventoryTradeCollectionService.collectItems(
                "trash",
                mock(Character.class),
                mock(Character.class),
                List::of,
                () -> groups(List.of(normal), List.of(other), List.of(self)),
                () -> new AmmoTradeGroups(List.of(), List.of()));
        List<Item> reservedPage = AgentInventoryTradeCollectionService.collectItems(
                "equips:reserved:1",
                mock(Character.class),
                mock(Character.class),
                List::of,
                () -> groups(List.of(normal), List.of(other), List.of(self)),
                () -> new AmmoTradeGroups(List.of(), List.of()));
        List<Item> reservedOther = AgentInventoryTradeCollectionService.collectItems(
                "equips:reserved_for_other",
                mock(Character.class),
                mock(Character.class),
                List::of,
                () -> groups(List.of(normal), List.of(other), List.of(self)),
                () -> new AmmoTradeGroups(List.of(), List.of()));

        assertEquals(List.of(normal, other, self), equips);
        assertEquals(List.of(normal), trash);
        assertEquals(List.of(self), reservedPage);
        assertEquals(List.of(other), reservedOther);
    }

    @Test
    void ammoCategoriesUseAgentAmmoGroupsInLegacyOrder() {
        Item nonOwn = item(2060000, (short) 1);
        Item own = item(2070000, (short) 2);
        AmmoTradeGroups groups = new AmmoTradeGroups(List.of(nonOwn), List.of(own));

        assertEquals(List.of(nonOwn, own), AgentInventoryTradeCollectionService.collectItems(
                "ammo", mock(Character.class), mock(Character.class), List::of,
                () -> groups(List.of(), List.of(), List.of()), () -> groups));
        assertEquals(List.of(nonOwn), AgentInventoryTradeCollectionService.collectItems(
                "ammo:non_own", mock(Character.class), mock(Character.class), List::of,
                () -> groups(List.of(), List.of(), List.of()), () -> groups));
        assertEquals(List.of(own), AgentInventoryTradeCollectionService.collectItems(
                "ammo:own", mock(Character.class), mock(Character.class), List::of,
                () -> groups(List.of(), List.of(), List.of()), () -> groups));
    }

    @Test
    void transferableAvailabilityHandlesMesosLikeLegacyInventory() {
        Character agent = mock(Character.class);
        when(agent.getMeso()).thenReturn(1000);

        assertTrue(AgentInventoryTradeCollectionService.hasTransferableItems(
                "mesos", agent, fragment -> 0, List::of));
        assertTrue(AgentInventoryTradeCollectionService.hasTransferableItems(
                "mesos:500", agent, fragment -> 0, List::of));
        assertFalse(AgentInventoryTradeCollectionService.hasTransferableItems(
                "mesos:1500", agent, fragment -> 0, List::of));
        assertEquals(1000, AgentInventoryTradeCollectionService.countTransferableItems(
                "mesos:500", agent, fragment -> 0, fragment -> 0, () -> 7));
    }

    @Test
    void transferableAvailabilityChecksEquippedNamedItemsBeforeBagItems() {
        Character agent = mock(Character.class);
        AtomicReference<String> fragmentSeen = new AtomicReference<>();
        AtomicBoolean collected = new AtomicBoolean(false);

        boolean hasItems = AgentInventoryTradeCollectionService.hasTransferableItems(
                "name:cape",
                agent,
                fragment -> {
                    fragmentSeen.set(fragment);
                    return 1;
                },
                () -> {
                    collected.set(true);
                    return List.of();
                });

        assertTrue(hasItems);
        assertEquals("cape", fragmentSeen.get());
        assertFalse(collected.get());
    }

    @Test
    void transferableCountAddsNamedBagAndEquippedSlotItems() {
        Character agent = mock(Character.class);
        AtomicInteger equippedCount = new AtomicInteger();

        int count = AgentInventoryTradeCollectionService.countTransferableItems(
                "name:cape",
                agent,
                fragment -> 3,
                fragment -> {
                    equippedCount.set(2);
                    return 2;
                },
                () -> 99);

        assertEquals(2, equippedCount.get());
        assertEquals(5, count);
    }

    private static AgentEquipTradeGroups groups(List<Item> normal, List<Item> other, List<Item> self) {
        return new AgentEquipTradeGroups(normal, other, self);
    }

    private static Item item(int itemId, short position) {
        Item item = mock(Item.class);
        when(item.getInventoryType()).thenReturn(InventoryType.EQUIP);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getPosition()).thenReturn(position);
        return item;
    }
}
