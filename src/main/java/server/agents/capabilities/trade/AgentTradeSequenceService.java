package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentInventoryRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

public final class AgentTradeSequenceService {
    private AgentTradeSequenceService() {
    }

    public static void startSequence(String category,
                                     Character recipient,
                                     List<Item> items,
                                     int mesos,
                                     boolean singleBatch,
                                     AgentRuntimeEntry entry,
                                     TradeBatchStarter batchStarter) {
        if (recipient == null) {
            AgentInventoryRuntime.replyNow(entry, AgentDialogueCatalog.tradeRecipientNotFoundReply());
            return;
        }
        AgentTradeStateService.initializeSequence(entry, category, recipient.getId(), singleBatch);
        batchStarter.open(items, mesos);
    }

    @FunctionalInterface
    public interface TradeBatchStarter {
        void open(List<Item> items, int mesos);
    }
}
