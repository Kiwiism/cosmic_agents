package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTradeItemCollectionServiceTest {
    @Test
    void collectsRecommendedItemsForEquipsRecommendedCategory() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Item recommended = mock(Item.class);
        AtomicBoolean recommendedCalled = new AtomicBoolean();

        List<Item> items = AgentTradeItemCollectionService.collectItems(
                "recommended",
                agent,
                owner,
                AgentTradeItemCollectionService.TradeItemCollectionCallbacks.of(
                        () -> {
                            recommendedCalled.set(true);
                            return List.of(recommended);
                        },
                        () -> new AgentEquipTradeGroups(List.of(), List.of(), List.of()),
                        () -> new AmmoTradeGroups(List.of(), List.of())),
                inventoryGateway());

        assertEquals(List.of(recommended), items);
        assertTrue(recommendedCalled.get());
    }

    @Test
    void collectsAmmoGroupsThroughCallback() {
        Character agent = mock(Character.class);
        Item nonOwnAmmo = mock(Item.class);
        AtomicBoolean ammoCalled = new AtomicBoolean();

        List<Item> items = AgentTradeItemCollectionService.collectItems(
                "ammo:non_own",
                agent,
                null,
                AgentTradeItemCollectionService.TradeItemCollectionCallbacks.of(
                        List::of,
                        () -> new AgentEquipTradeGroups(List.of(), List.of(), List.of()),
                        () -> {
                            ammoCalled.set(true);
                            return new AmmoTradeGroups(List.of(nonOwnAmmo), List.of());
                        }),
                inventoryGateway());

        assertEquals(List.of(nonOwnAmmo), items);
        assertTrue(ammoCalled.get());
    }

    private static InventoryGateway inventoryGateway() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(inventory.isQuestItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
        return inventory;
    }
}
