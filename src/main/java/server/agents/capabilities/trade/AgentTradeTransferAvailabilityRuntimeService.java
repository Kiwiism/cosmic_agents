package server.agents.capabilities.trade;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class AgentTradeTransferAvailabilityRuntimeService {
    private AgentTradeTransferAvailabilityRuntimeService() {
    }

    public static boolean hasTransferableItems(String category,
                                               AgentRuntimeEntry entry,
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
                                             AgentRuntimeEntry entry,
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
        Character owner(AgentRuntimeEntry entry);

        int countNamedItems(Character agent, String fragment);

        int countEquippedSlotItems(Character agent, String fragment);

        static RuntimeCallbacks of(Function<AgentRuntimeEntry, Character> owner,
                                   BiFunction<Character, String, Integer> countNamedItems,
                                   BiFunction<Character, String, Integer> countEquippedSlotItems) {
            return new RuntimeCallbacks() {
                @Override
                public Character owner(AgentRuntimeEntry entry) {
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
