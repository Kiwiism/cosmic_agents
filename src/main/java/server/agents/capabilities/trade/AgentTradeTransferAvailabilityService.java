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
        return hasTransferableItems(
                category,
                agent,
                TransferAvailabilityCallbacks.of(fragment -> 0, countEquippedSlotItems, collectItems));
    }

    public static boolean hasTransferableItems(String category,
                                               Character agent,
                                               TransferAvailabilityCallbacks callbacks) {
        return AgentInventoryTradeCollectionService.hasTransferableItems(
                category,
                agent,
                callbacks::countEquippedSlotItems,
                callbacks::collectItems);
    }

    public static int countTransferableItems(String category,
                                             Character agent,
                                             Function<String, Integer> countNamedItems,
                                             Function<String, Integer> countEquippedSlotItems,
                                             Supplier<List<Item>> collectItems) {
        return countTransferableItems(
                category,
                agent,
                TransferAvailabilityCallbacks.of(countNamedItems, countEquippedSlotItems, collectItems));
    }

    public static int countTransferableItems(String category,
                                             Character agent,
                                             TransferAvailabilityCallbacks callbacks) {
        return AgentInventoryTradeCollectionService.countTransferableItems(
                category,
                agent,
                callbacks::countNamedItems,
                callbacks::countEquippedSlotItems,
                () -> AgentInventoryTradePolicy.itemQuantitySum(callbacks.collectItems()));
    }

    public interface TransferAvailabilityCallbacks {
        int countNamedItems(String fragment);
        int countEquippedSlotItems(String fragment);
        List<Item> collectItems();

        static TransferAvailabilityCallbacks of(Function<String, Integer> countNamedItems,
                                                Function<String, Integer> countEquippedSlotItems,
                                                Supplier<List<Item>> collectItems) {
            return new TransferAvailabilityCallbacks() {
                @Override
                public int countNamedItems(String fragment) {
                    return countNamedItems.apply(fragment);
                }

                @Override
                public int countEquippedSlotItems(String fragment) {
                    return countEquippedSlotItems.apply(fragment);
                }

                @Override
                public List<Item> collectItems() {
                    return collectItems.get();
                }
            };
        }
    }
}
