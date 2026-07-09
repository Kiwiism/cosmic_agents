package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventoryTradeRuntimeServiceTest {
    @Test
    void collectItemsUsesLegacyRecommendedCollectorWhenOwnerExists() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Item recommended = mock(Item.class);
        AtomicBoolean recommendationCalled = new AtomicBoolean();

        List<Item> items = AgentInventoryTradeRuntimeService.collectItems(
                "recommended",
                agent,
                owner,
                AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                        (receiver, holder) -> {
                            recommendationCalled.set(true);
                            assertEquals(owner, receiver);
                            assertEquals(agent, holder);
                            return List.of(recommended);
                        },
                        ignored -> WeaponType.NOT_A_WEAPON,
                        ignored -> 0,
                        ignored -> false,
                        () -> false,
                        () -> false,
                        ignored -> Set.of(),
                        ignored -> false,
                        () -> owner,
                        AgentInventoryTradeRuntimeServiceTest::inventoryGateway));

        assertEquals(List.of(recommended), items);
        assertTrue(recommendationCalled.get());
    }

    @Test
    void classifyAmmoGroupsUsesRuntimeHooks() {
        Character agent = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item ownArrow = item(2060000, 10);
        Item otherArrow = item(2061000, 10);
        Item questBullet = item(2330000, 10);
        AtomicBoolean weaponRead = new AtomicBoolean();
        AtomicBoolean tradeableRead = new AtomicBoolean();
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 3);
        when(use.getItem((short) 1)).thenReturn(ownArrow);
        when(use.getItem((short) 2)).thenReturn(otherArrow);
        when(use.getItem((short) 3)).thenReturn(questBullet);

        AmmoTradeGroups groups = AgentInventoryTradeRuntimeService.classifyAmmoTradeGroups(
                agent,
                AgentInventoryTradeRuntimeService.RuntimeCallbacks.of(
                        (owner, holder) -> List.of(),
                        ignored -> {
                            weaponRead.set(true);
                            return WeaponType.BOW;
                        },
                        ignored -> 1,
                        itemId -> itemId == 2330000,
                        () -> {
                            tradeableRead.set(true);
                            return false;
                        },
                        () -> false,
                        ignored -> Set.of(),
                        ignored -> false,
                        () -> null,
                        AgentInventoryTradeRuntimeServiceTest::inventoryGateway));

        assertEquals(List.of(otherArrow), groups.nonOwn());
        assertEquals(List.of(ownArrow), groups.own());
        assertTrue(weaponRead.get());
        assertTrue(tradeableRead.get());
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }

    private static InventoryGateway inventoryGateway() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(inventory.isQuestItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        return inventory;
    }
}
