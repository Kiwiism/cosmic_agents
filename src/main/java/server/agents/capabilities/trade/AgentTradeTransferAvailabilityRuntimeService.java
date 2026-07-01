package server.agents.capabilities.trade;

import client.Character;
import server.bots.BotEntry;

import java.util.function.BiFunction;

public final class AgentTradeTransferAvailabilityRuntimeService {
    private AgentTradeTransferAvailabilityRuntimeService() {
    }

    public static boolean hasTransferableItems(String category,
                                               BotEntry entry,
                                               Character agent,
                                               RuntimeCallbacks callbacks,
                                               AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks) {
        return AgentTradeTransferAvailabilityService.hasTransferableItems(
                category,
                agent,
                AgentTradeTransferAvailabilityCallbackService.transferAvailabilityCallbacks(
                        fragment -> 0,
                        fragment -> callbacks.countEquippedSlotItems(agent, fragment),
                        () -> AgentInventoryTradeRuntimeService.collectItems(
                                category,
                                agent,
                                callbacks.owner(entry),
                                inventoryCallbacks)));
    }

    public static int countTransferableItems(String category,
                                             BotEntry entry,
                                             Character agent,
                                             RuntimeCallbacks callbacks,
                                             AgentInventoryTradeRuntimeService.RuntimeCallbacks inventoryCallbacks) {
        return AgentTradeTransferAvailabilityService.countTransferableItems(
                category,
                agent,
                AgentTradeTransferAvailabilityCallbackService.transferAvailabilityCallbacks(
                        fragment -> callbacks.countNamedItems(agent, fragment),
                        fragment -> callbacks.countEquippedSlotItems(agent, fragment),
                        () -> AgentInventoryTradeRuntimeService.collectItems(
                                category,
                                agent,
                                callbacks.owner(entry),
                                inventoryCallbacks)));
    }

    public interface RuntimeCallbacks {
        Character owner(BotEntry entry);

        int countNamedItems(Character agent, String fragment);

        int countEquippedSlotItems(Character agent, String fragment);

        static RuntimeCallbacks of(java.util.function.Function<BotEntry, Character> owner,
                                   BiFunction<Character, String, Integer> countNamedItems,
                                   BiFunction<Character, String, Integer> countEquippedSlotItems) {
            return new RuntimeCallbacks() {
                @Override
                public Character owner(BotEntry entry) {
                    return owner.apply(entry);
                }

                @Override
                public int countNamedItems(Character agent, String fragment) {
                    return countNamedItems.apply(agent, fragment);
                }

                @Override
                public int countEquippedSlotItems(Character agent, String fragment) {
                    return countEquippedSlotItems.apply(agent, fragment);
                }
            };
        }
    }
}
