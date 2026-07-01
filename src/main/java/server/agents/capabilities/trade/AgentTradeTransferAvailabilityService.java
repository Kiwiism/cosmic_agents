package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AgentTradeTransferAvailabilityService {
    private AgentTradeTransferAvailabilityService() {
    }

    public static boolean hasTransferableItems(String category,
                                               Character agent,
                                               Function<String, Integer> countEquippedSlotItems,
                                               Supplier<List<Item>> collectItems) {
        return AgentInventoryTradeCollectionService.hasTransferableItems(
                category,
                agent,
                countEquippedSlotItems,
                collectItems);
    }

    public static int countTransferableItems(String category,
                                             Character agent,
                                             Function<String, Integer> countNamedItems,
                                             Function<String, Integer> countEquippedSlotItems,
                                             Supplier<List<Item>> collectItems) {
        return AgentInventoryTradeCollectionService.countTransferableItems(
                category,
                agent,
                countNamedItems,
                countEquippedSlotItems,
                () -> AgentInventoryTradePolicy.itemQuantitySum(collectItems.get()));
    }
}
