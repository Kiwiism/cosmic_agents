package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.inventory.AgentInventoryTradeCollectionService.PreparedTradeItems;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentPreparedTradeTransferService {
    private AgentPreparedTradeTransferService() {
    }

    public static void startPreparedTradeTransfer(String category,
                                                  PreparedTradeItems prepared,
                                                  Supplier<Boolean> restoreSlots,
                                                  StartTradeSequence startTradeSequence,
                                                  Consumer<String> reply) {
        if (prepared.errorMessage() != null) {
            reply.accept(prepared.errorMessage());
            return;
        }

        List<Item> items = prepared.items();
        if (items.isEmpty()) {
            reply.accept(AgentInventoryDialogueReporter.noItemsReply(category));
            return;
        }

        startTradeSequence.start(category, items, restoreSlots.get());
    }

    @FunctionalInterface
    public interface StartTradeSequence {
        void start(String category, List<Item> items, boolean restoreSlots);
    }
}
