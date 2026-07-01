package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentTradeItemCollectionCallbackServiceTest {
    @Test
    void buildsCollectionCallbacksFromLegacyOperations() {
        List<Item> recommended = List.of(mock(Item.class));
        AgentEquipTradeGroups equipGroups = new AgentEquipTradeGroups(List.of(), List.of(), List.of());
        AmmoTradeGroups ammoGroups = new AmmoTradeGroups(List.of(), List.of());

        AgentTradeItemCollectionService.TradeItemCollectionCallbacks callbacks =
                AgentTradeItemCollectionCallbackService.tradeItemCollectionCallbacks(
                        () -> recommended,
                        () -> equipGroups,
                        () -> ammoGroups);

        assertSame(recommended, callbacks.recommendedItems());
        assertSame(equipGroups, callbacks.equipTradeGroups());
        assertSame(ammoGroups, callbacks.ammoTradeGroups());
    }
}
