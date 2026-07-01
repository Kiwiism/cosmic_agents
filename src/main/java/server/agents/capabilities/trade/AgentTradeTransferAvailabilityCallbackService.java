package server.agents.capabilities.trade;

import client.inventory.Item;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class AgentTradeTransferAvailabilityCallbackService {
    private AgentTradeTransferAvailabilityCallbackService() {
    }

    public static AgentTradeTransferAvailabilityService.TransferAvailabilityCallbacks transferAvailabilityCallbacks(
            Function<String, Integer> countNamedItems,
            Function<String, Integer> countEquippedSlotItems,
            Supplier<List<Item>> collectItems) {
        return AgentTradeTransferAvailabilityService.TransferAvailabilityCallbacks.of(
                countNamedItems,
                countEquippedSlotItems,
                collectItems);
    }
}
