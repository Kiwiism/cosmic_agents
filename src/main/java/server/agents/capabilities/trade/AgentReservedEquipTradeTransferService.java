package server.agents.capabilities.trade;

import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AgentReservedEquipTradeTransferService {
    private AgentReservedEquipTradeTransferService() {
    }

    public static void startReservedEquipTradeTransfer(String category,
                                                       List<Item> items,
                                                       Supplier<String> categoryMessage,
                                                       BiConsumer<String, List<Item>> startTradeSequence,
                                                       Consumer<String> setCategoryMessage,
                                                       Consumer<String> reply) {
        if (items.isEmpty()) {
            reply.accept(AgentInventoryDialogueReporter.noItemsReply(category));
            return;
        }

        startTradeSequence.accept(category, items);
        setCategoryMessage.accept(categoryMessage.get());
    }
}
